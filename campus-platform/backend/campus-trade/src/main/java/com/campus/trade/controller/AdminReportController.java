package com.campus.trade.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Report;
import com.campus.trade.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理端举报接口：查看举报列表并处理（需 ADMIN 角色） */
@RestController
@RequestMapping("/api/admin/reports")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    /** 分页查询全部举报，可按处理状态过滤 */
    @GetMapping
    public R<Page<Report>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(reportService.listReports(status, page, size));
    }

    /** 处理举报：记录处理结果并更新状态 */
    @PutMapping("/{id}/handle")
    public R<Void> handle(@PathVariable Long id, @RequestBody HandleReq req) {
        reportService.handleReport(id, req.result, req.status);
        return R.ok();
    }

    /** 举报处理请求体（内联DTO） */
    public static class HandleReq {
        /** 处理结果说明 */
        public String result;
        /** 更新后的举报状态 */
        public int status;
    }
}
