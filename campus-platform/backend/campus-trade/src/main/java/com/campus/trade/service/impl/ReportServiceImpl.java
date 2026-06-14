package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.entity.Report;
import com.campus.trade.mapper.ReportMapper;
import com.campus.trade.service.ReportService;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    private static final Set<String> VALID_TARGET_TYPES = Set.of("goods", "user", "message", "post", "comment");

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
        report.setEvidence(evidence);
        report.setStatus(0);
        save(report);
    }

    @Override
    public Page<Report> listReports(Integer status, int page, int size) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Report::getStatus, status);
        wrapper.orderByDesc(Report::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Page<Report> listMyReports(Long reporterId, Integer status, int page, int size) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId);
        if (status != null) wrapper.eq(Report::getStatus, status);
        wrapper.orderByDesc(Report::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

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
