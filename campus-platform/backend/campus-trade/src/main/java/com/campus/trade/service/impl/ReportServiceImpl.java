package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.moderation.ReportCreatedEvent;
import com.campus.trade.entity.Report;
import com.campus.trade.mapper.ReportMapper;
import com.campus.trade.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Set;

/** 举报服务实现，覆盖举报提交与平台处理 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    private final ApplicationEventPublisher eventPublisher;

    /** 允许的举报对象类型 */
    private static final Set<String> VALID_TARGET_TYPES = Set.of("goods", "user", "message", "post", "comment");

    /** 提交举报，同人同目标仅允许存在一条待处理举报 */
    @Override
    public void createReport(Long reporterId, String targetType, Long targetId, String reason, String evidence) {
        // 校验 targetType 合法性
        if (targetType == null || !VALID_TARGET_TYPES.contains(targetType)) {
            throw new BusinessException("无效的举报类型，必须为: " + String.join(", ", VALID_TARGET_TYPES));
        }
        if (targetId == null || targetId <= 0) {
            throw new BusinessException("举报目标ID无效");
        }

        // 防止重复提交：检查是否已有待处理的举报
        Long existing = count(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId)
                .eq(Report::getStatus, 0));
        if (existing > 0) {
            throw new BusinessException("您已对该目标提交过举报，请等待处理结果");
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason);

        // ✅ 【核心修复】根据项目实际使用的 JSON 库选择以下【其中一行】即可：
        // [Spring Boot 默认 Jackson]
        // report.setEvidence(StringUtils.isBlank(evidence) ? null : new ObjectMapper().writeValueAsString(evidence));

        // [Hutool 工具库]
        // report.setEvidence(StringUtils.isBlank(evidence) ? null : cn.hutool.json.JSONUtil.toJsonStr(evidence));

        // [Fastjson / Fastjson2]
        // report.setEvidence(StringUtils.isBlank(evidence) ? null : com.alibaba.fastjson2.JSON.toJSONString(evidence));

        // [如果 evidence 只是纯文本且允许改表结构，最推荐的做法其实是把数据库字段改为 VARCHAR/TEXT，然后直接赋值：]
        report.setEvidence(StringUtils.isBlank(evidence) ? null : evidence);

        report.setStatus(0);
        save(report);

        // 评论类举报触发内容安全 AI 审核闭环（campus-app 的 ModerationOrchestrator 监听）
        if ("comment".equals(targetType)) {
            eventPublisher.publishEvent(new ReportCreatedEvent(
                    report.getId(), targetType, targetId, reporterId, reason));
        }
    }

    /** 分页查询举报列表（管理端），按创建时间倒序 */
    @Override
    public Page<Report> listReports(Integer status, int page, int size) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Report::getStatus, status);
        wrapper.orderByDesc(Report::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /** 分页查询我提交的举报，按创建时间倒序 */
    @Override
    public Page<Report> listMyReports(Long reporterId, Integer status, int page, int size) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId);
        if (status != null) wrapper.eq(Report::getStatus, status);
        wrapper.orderByDesc(Report::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /** 处理举报，仅待处理状态可处理，处理后为终态 */
    @Override
    public void handleReport(Long reportId, String result, int status) {
        Report report = getById(reportId);
        if (report == null) throw new BusinessException("举报不存在");
        if (report.getStatus() != 0) {
            throw new BusinessException("该举报已处理完毕");
        }
        // 校验 status 只能为 1（已处理）或 2（已驳回）
        if (status != 1 && status != 2) {
            throw new BusinessException("无效的处理状态，只能为 1（已处理）或 2（已驳回）");
        }
        report.setResult(result);
        report.setStatus(status);
        updateById(report);
    }
}
