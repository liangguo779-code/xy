package com.campus.app.moderation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.entity.ContentAuditLog;
import com.campus.common.moderation.AuditLogService;
import com.campus.common.moderation.ContentAuditClient;
import com.campus.common.moderation.ModerationProperties;
import com.campus.common.moderation.ReportCreatedEvent;
import com.campus.forum.entity.Comment;
import com.campus.forum.mapper.CommentMapper;
import com.campus.trade.entity.Report;
import com.campus.trade.mapper.ReportMapper;
import com.campus.trade.service.ReportService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内容安全编排器（"机器自动拦截 + 人工复核"闭环的核心流转）：
 *
 * <p>评论被举报 → 先隐藏（status=3 待人审，安全优先）→ 异步调 AI 审核 API：
 * <ul>
 *   <li>高置信违规（conf ≥ aiVerifyThreshold）→ 自动拦截（status=2），举报办结；</li>
 *   <li>高置信合规（conf ≥ aiClearThreshold）→ 恢复展示（status=1），举报驳回；</li>
 *   <li>不确定/低置信/AI 失败 → 留在待人审（status=3），举报留待人工处理。</li>
 * </ul>
 * 全部流转写 content_audit_log；同一评论已在待审态时不重复触发 AI（去重）。
 *
 * <p>放在 campus-app 是因为它同时依赖 forum（评论）、trade（举报）、ai（LLM 裁决），
 * 而业务模块彼此不依赖，campus-app 是唯一的聚合层。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationOrchestrator {

    private final CommentMapper commentMapper;
    private final ReportMapper reportMapper;
    private final ReportService reportService;
    private final ContentAuditClient contentAuditClient;
    private final AuditLogService auditLogService;
    private final ModerationProperties props;

    /** AI 审核异步线程池（调 LLM 秒级耗时，不能阻塞举报接口） */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "moderation-ai");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** 举报创建事件入口：仅处理评论类举报。 */
    @EventListener
    public void onReportCreated(ReportCreatedEvent event) {
        if (!"comment".equals(event.targetType())) return;
        executor.execute(() -> {
            try {
                auditReportedComment(event);
            } catch (Exception e) {
                log.error("举报机审流程异常: report={}", event.reportId(), e);
            }
        });
    }

    private void auditReportedComment(ReportCreatedEvent event) {
        Comment comment = commentMapper.selectById(event.targetId());
        if (comment == null) {
            closeOpenReports(event.targetId(), "举报目标不存在", 2);
            return;
        }
        // 仅 status=1（正常展示）进入机审；已是 2/3/4 的不重复触发（待审去重）
        if (comment.getStatus() == null || comment.getStatus() != 1) {
            log.info("评论已处于非正常态（status={}），跳过机审: comment={}", comment.getStatus(), comment.getId());
            return;
        }

        // 先隐藏进人审态（安全优先），AI 结论出来后再流转
        comment.setStatus(3);
        commentMapper.updateById(comment);

        ContentAuditLog entry = new ContentAuditLog();
        entry.setTargetType("comment");
        entry.setTargetId(comment.getId());
        entry.setTriggerType(AuditLogService.TRIGGER_REPORT_AI);
        entry.setContent(comment.getContent());

        try {
            ContentAuditClient.AiVerdict v = contentAuditClient.audit(comment.getContent(), event.reason());
            entry.setAiVerdict(v.verdict());
            entry.setAiConfidence(BigDecimal.valueOf(v.confidence()));
            entry.setAiReason(v.reason());

            if (v.verdict() == 1 && v.confidence() >= props.getAiVerifyThreshold()) {
                // 高置信违规 → 自动拦截
                comment.setStatus(2);
                commentMapper.updateById(comment);
                entry.setFinalStatus(2);
                closeOpenReports(comment.getId(), "机审判定违规：" + v.reason(), 1);
                log.info("机审自动拦截: comment={} conf={}", comment.getId(), v.confidence());
            } else if (v.verdict() == 0 && v.confidence() >= props.getAiClearThreshold()) {
                // 高置信合规 → 恢复展示
                comment.setStatus(1);
                commentMapper.updateById(comment);
                entry.setFinalStatus(1);
                closeOpenReports(comment.getId(), "机审判定合规：" + v.reason(), 2);
                log.info("机审自动放行: comment={} conf={}", comment.getId(), v.confidence());
            } else {
                // 不确定/低置信 → 转人审
                entry.setFinalStatus(3);
                log.info("机审转人审: comment={} verdict={} conf={}", comment.getId(), v.verdict(), v.confidence());
            }
        } catch (Exception e) {
            // AI 调用失败 fail-safe 到人审，绝不静默放行或错杀
            log.warn("AI 审核调用失败，转人审: comment={}", comment.getId(), e);
            entry.setAiVerdict(2);
            entry.setAiReason("AI 审核调用失败: " + e.getMessage());
            entry.setFinalStatus(3);
        }
        auditLogService.record(entry);
    }

    /**
     * 人工修正评论状态（管理端复核台）：任意置 0-4 并写审计流水，
     * 同步办结该评论全部待处理举报（恢复→驳回举报，违规/删除→办结举报）。
     */
    public void manualFixStatus(Long commentId, int status, String note, Long operatorId) {
        if (status < 0 || status > 4) {
            throw new com.campus.common.exception.BusinessException("无效的状态值，只能为 0-4");
        }
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new com.campus.common.exception.BusinessException("评论不存在");
        }
        comment.setStatus(status);
        commentMapper.updateById(comment);

        ContentAuditLog entry = new ContentAuditLog();
        entry.setTargetType("comment");
        entry.setTargetId(commentId);
        entry.setTriggerType(AuditLogService.TRIGGER_HUMAN);
        entry.setContent(comment.getContent());
        entry.setFinalStatus(status);
        entry.setReviewerId(operatorId);
        entry.setReviewNote(note);
        auditLogService.record(entry);

        if (status == 1 || status == 0) {
            closeOpenReports(commentId, note == null || note.isBlank() ? "人工复核通过" : note, 2);
        } else {
            closeOpenReports(commentId, note == null || note.isBlank() ? "人工确认违规" : note, 1);
        }
    }

    /** 办结某评论全部待处理举报（reportStatus: 1-已处理 2-已驳回）。 */
    private void closeOpenReports(Long commentId, String result, int reportStatus) {
        List<Report> open = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetType, "comment")
                .eq(Report::getTargetId, commentId)
                .eq(Report::getStatus, 0));
        for (Report r : open) {
            try {
                reportService.handleReport(r.getId(), result, reportStatus);
            } catch (Exception e) {
                log.warn("举报办结失败 report={}: {}", r.getId(), e.getMessage());
            }
        }
    }
}
