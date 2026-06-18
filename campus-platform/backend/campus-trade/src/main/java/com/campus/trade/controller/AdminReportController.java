package com.campus.trade.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Report;
import com.campus.trade.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public R<Page<Report>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(reportService.listReports(status, page, size));
    }

    @PutMapping("/{id}/handle")
    public R<Void> handle(@PathVariable Long id, @RequestBody HandleReq req) {
        reportService.handleReport(id, req.result, req.status);
        return R.ok();
    }

    public static class HandleReq {
        public String result;
        public int status;
    }
}
