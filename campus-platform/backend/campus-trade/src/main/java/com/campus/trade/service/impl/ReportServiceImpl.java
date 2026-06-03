package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.entity.Report;
import com.campus.trade.mapper.ReportMapper;
import com.campus.trade.service.ReportService;
import org.springframework.stereotype.Service;

@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    @Override
    public void createReport(Long reporterId, String targetType, Long targetId, String reason, String evidence) {
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
    public void handleReport(Long reportId, String result, int status) {
        Report report = getById(reportId);
        if (report == null) throw new BusinessException("举报不存在");
        if (report.getStatus() != 0) {
            throw new BusinessException("该举报已处理完毕");
        }
        report.setResult(result);
        report.setStatus(status);
        updateById(report);
    }
}
