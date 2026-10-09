package com.campus.app.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.app.moderation.ModerationOrchestrator;
import com.campus.common.entity.ContentAuditLog;
import com.campus.common.entity.User;
import com.campus.common.mapper.ContentAuditLogMapper;
import com.campus.common.mapper.UserMapper;
import com.campus.common.moderation.AuditLogService;
import com.campus.common.result.R;
import com.campus.forum.entity.Comment;
import com.campus.forum.mapper.CommentMapper;
import com.campus.trade.entity.Report;
import com.campus.trade.mapper.ReportMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理后台-内容安全复核台（仅 ADMIN）：
 * 人审队列、审核详情、人工修正评论状态、KPI 统计。
 */
@SaCheckRole("ADMIN")
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final ContentAuditLogMapper auditMapper;
    private final ReportMapper reportMapper;
    private final AuditLogService auditLogService;
    private final ModerationOrchestrator orchestrator;

    /** 人审队列：按评论状态筛选（默认待人审 status=3），含作者昵称 */
    @GetMapping("/comments")
    public R<Map<String, Object>> queue(@RequestParam(defaultValue = "3") int status,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        Page<Comment> p = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getStatus, status)
                        .orderByDesc(Comment::getId));
        // 批量回填作者昵称
        Set<Long> userIds = new HashSet<>();
        for (Comment c : p.getRecords()) userIds.add(c.getUserId());
        Map<Long, String> nicknames = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(userIds)) {
                nicknames.put(u.getId(), u.getNickname());
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Comment c : p.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("postId", c.getPostId());
            row.put("userId", c.getUserId());
            row.put("nickname", nicknames.getOrDefault(c.getUserId(), String.valueOf(c.getUserId())));
            row.put("content", c.getContent());
            row.put("status", c.getStatus());
            row.put("createTime", c.getCreateTime());
            rows.add(row);
        }
        return R.ok(Map.of("total", p.getTotal(), "rows", rows));
    }

    /** 审核详情：评论 + 全部审计流水 + 相关举报 */
    @GetMapping("/comment/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) return R.fail("评论不存在");
        User author = userMapper.selectById(comment.getUserId());
        List<ContentAuditLog> logs = auditLogService.listByTarget("comment", id);
        List<Report> reports = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetType, "comment")
                .eq(Report::getTargetId, id)
                .orderByDesc(Report::getId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("comment", comment);
        data.put("authorNickname", author == null ? null : author.getNickname());
        data.put("auditLogs", logs);
        data.put("reports", reports);
        return R.ok(data);
    }

    /** 人工修正评论状态（0-4 任意流转，写审计流水并联动办结举报） */
    @PutMapping("/comment/{id}/status")
    public R<Void> fixStatus(@PathVariable Long id, @RequestBody FixStatusReq req) {
        orchestrator.manualFixStatus(id, req.status, req.note, StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    /** KPI 统计：识别准确率（1-人审改判率）、人审减负率（机审自动结案占比）等 */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        List<ContentAuditLog> machine = auditMapper.selectList(new LambdaQueryWrapper<ContentAuditLog>()
                .eq(ContentAuditLog::getTriggerType, AuditLogService.TRIGGER_REPORT_AI)
                .orderByAsc(ContentAuditLog::getId));
        List<ContentAuditLog> human = auditMapper.selectList(new LambdaQueryWrapper<ContentAuditLog>()
                .eq(ContentAuditLog::getTriggerType, AuditLogService.TRIGGER_HUMAN)
                .orderByAsc(ContentAuditLog::getId));

        int machineTotal = machine.size();
        int machineAutoClosed = 0;
        int machineToHuman = 0;
        for (ContentAuditLog m : machine) {
            if (m.getFinalStatus() != null && (m.getFinalStatus() == 1 || m.getFinalStatus() == 2)) {
                machineAutoClosed++;
            } else {
                machineToHuman++;
            }
        }

        // 识别准确率：人审结论与该目标上一次机审判定的一致率
        // （机审放行(1) 人审维持 1 = 一致；机审拦截(2) 人审维持 2/4 = 一致；其余为改判）
        Map<String, Integer> lastMachineDecision = new HashMap<>();
        for (ContentAuditLog m : machine) {
            if (m.getFinalStatus() != null && (m.getFinalStatus() == 1 || m.getFinalStatus() == 2)) {
                lastMachineDecision.put(m.getTargetType() + ":" + m.getTargetId(), m.getFinalStatus());
            }
        }
        int agree = 0, disagree = 0;
        for (ContentAuditLog h : human) {
            if (h.getFinalStatus() == null || h.getFinalStatus() == 3) continue;
            Integer m = lastMachineDecision.remove(h.getTargetType() + ":" + h.getTargetId());
            if (m == null) continue;
            boolean consistent = (m == 1 && h.getFinalStatus() == 1)
                    || (m == 2 && (h.getFinalStatus() == 2 || h.getFinalStatus() == 4));
            if (consistent) agree++; else disagree++;
        }
        double accuracy = (agree + disagree) == 0 ? 1.0 : agree / (double) (agree + disagree);
        double autoResolveRate = machineTotal == 0 ? 0 : machineAutoClosed / (double) machineTotal;

        long pendingReview = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, 3));
        Long publishHits = auditMapper.selectCount(new LambdaQueryWrapper<ContentAuditLog>()
                .eq(ContentAuditLog::getTriggerType, AuditLogService.TRIGGER_PUBLISH));
        Long publishBlocked = auditMapper.selectCount(new LambdaQueryWrapper<ContentAuditLog>()
                .eq(ContentAuditLog::getTriggerType, AuditLogService.TRIGGER_PUBLISH)
                .eq(ContentAuditLog::getFinalStatus, 0));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("machineTotal", machineTotal);
        data.put("machineAutoClosed", machineAutoClosed);
        data.put("machineToHuman", machineToHuman);
        data.put("humanReviewed", human.size());
        data.put("humanAgree", agree);
        data.put("humanOverturned", disagree);
        data.put("accuracy", accuracy);
        data.put("autoResolveRate", autoResolveRate);
        data.put("pendingReview", pendingReview);
        data.put("publishHits", publishHits);
        data.put("publishBlocked", publishBlocked);
        return R.ok(data);
    }

    /** 人工修正状态请求 */
    @Data
    public static class FixStatusReq {
        /** 目标状态：0-已删除 1-正常 2-机审拦截 3-待人审 4-人审违规 */
        public int status;
        /** 修正理由/备注 */
        public String note;
    }
}
