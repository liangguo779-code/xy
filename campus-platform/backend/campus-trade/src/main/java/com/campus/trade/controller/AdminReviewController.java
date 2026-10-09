package com.campus.trade.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Review;
import com.campus.trade.service.AdminReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理端评价接口：处理评价申诉、上下架评价、查看统计（需 ADMIN 角色） */
@RestController
@RequestMapping("/api/admin/reviews")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    /** 分页查询评价申诉列表，可按申诉状态过滤 */
    @GetMapping("/appeals")
    public R<Page<Review>> listAppeals(
            @RequestParam(required = false) Integer appealStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(adminReviewService.listAppeals(appealStatus, page, size));
    }

    /** 处理申诉：通过则隐藏/恢复评价，记录处理结论 */
    @PutMapping("/{id}/appeal")
    public R<Void> handleAppeal(@PathVariable Long id, @RequestBody AppealHandleReq req) {
        adminReviewService.handleAppeal(id, StpUtil.getLoginIdAsLong(), req.approved, req.result);
        return R.ok();
    }

    /** 直接修改评价状态（显示/隐藏） */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        adminReviewService.updateStatus(id, req.status);
        return R.ok();
    }

    /** 评价总览统计：总数/申诉中/已隐藏 */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        long[] s = adminReviewService.getStats();
        return R.ok(Map.of("total", s[0], "appealed", s[1], "hidden", s[2]));
    }

    /** 申诉处理请求体（内联DTO） */
    public static class AppealHandleReq {
        /** 是否支持申诉（true-申诉成立） */
        public boolean approved;
        /** 处理结论说明 */
        public String result;
    }

    /** 状态修改请求体（内联DTO） */
    public static class StatusReq {
        /** 目标状态值（评价 显示/隐藏） */
        public int status;
    }
}
