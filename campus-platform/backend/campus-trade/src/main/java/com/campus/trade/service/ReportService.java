package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Report;

public interface ReportService extends IService<Report> {
    void createReport(Long reporterId, String targetType, Long targetId, String reason, String evidence);
    Page<Report> listReports(Integer status, int page, int size);
    void handleReport(Long reportId, String result, int status);
}
