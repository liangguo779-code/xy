package com.campus.trade.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Dispute;
import com.campus.trade.service.DisputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/disputes")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final DisputeService disputeService;

    @GetMapping
    public R<Page<Dispute>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(disputeService.listDisputes(status, page, size));
    }

    @PutMapping("/{id}/resolve")
    public R<Void> resolve(@PathVariable Long id, @RequestBody ResolveReq req) {
        disputeService.resolveDispute(id, StpUtil.getLoginIdAsLong(), req.result, req.status);
        return R.ok();
    }

    public static class ResolveReq {
        public String result;
        public int status;
    }
}
