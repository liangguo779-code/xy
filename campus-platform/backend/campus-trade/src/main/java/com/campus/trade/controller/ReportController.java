package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Report;
import com.campus.trade.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public R<Void> create(@RequestBody CreateReportReq req) {
        reportService.createReport(StpUtil.getLoginIdAsLong(), req.targetType, req.targetId, req.reason, req.evidence);
        return R.ok();
    }

    @GetMapping("/my")
    public R<Page<Report>> myReports(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(reportService.listMyReports(userId, status, page, size));
    }

    public static class CreateReportReq {
        public String targetType;
        public Long targetId;
        public String reason;
        public String evidence;
    }
}
