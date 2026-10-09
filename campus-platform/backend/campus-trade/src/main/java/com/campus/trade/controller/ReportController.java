package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Report;
import com.campus.trade.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 举报接口：提交举报并查看自己的举报进度 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 提交举报（目标可为商品/用户/评论等） */
    @PostMapping
    public R<Void> create(@RequestBody CreateReportReq req) {
        reportService.createReport(StpUtil.getLoginIdAsLong(), req.targetType, req.targetId, req.reason, req.evidence);
        return R.ok();
    }

    /** 分页查询我的举报，可按处理状态过滤 */
    @GetMapping("/my")
    public R<Page<Report>> myReports(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(reportService.listMyReports(userId, status, page, size));
    }

    /** 举报请求体（内联DTO） */
    public static class CreateReportReq {
        /** 举报目标类型：goods/user/comment 等 */
        public String targetType;
        /** 举报目标ID */
        public Long targetId;
        /** 举报理由 */
        public String reason;
        /** 证据材料（图片URL或文字说明），可空 */
        public String evidence;
    }
}
