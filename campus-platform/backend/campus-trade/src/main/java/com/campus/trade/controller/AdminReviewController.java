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

@RestController
@RequestMapping("/api/admin/reviews")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    @GetMapping("/appeals")
    public R<Page<Review>> listAppeals(
            @RequestParam(required = false) Integer appealStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(adminReviewService.listAppeals(appealStatus, page, size));
    }

    @PutMapping("/{id}/appeal")
    public R<Void> handleAppeal(@PathVariable Long id, @RequestBody AppealHandleReq req) {
        adminReviewService.handleAppeal(id, StpUtil.getLoginIdAsLong(), req.approved, req.result);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        adminReviewService.updateStatus(id, req.status);
        return R.ok();
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        long[] s = adminReviewService.getStats();
        return R.ok(Map.of("total", s[0], "appealed", s[1], "hidden", s[2]));
    }

    public static class AppealHandleReq {
        public boolean approved;
        public String result;
    }

    public static class StatusReq {
        public int status;
    }
}
