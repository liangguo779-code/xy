package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Dispute;
import com.campus.trade.service.DisputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping
    public R<Void> create(@RequestBody CreateDisputeReq req) {
        disputeService.createDispute(StpUtil.getLoginIdAsLong(), req.orderId, req.reason, req.evidenceImages);
        return R.ok();
    }

    @GetMapping("/my")
    public R<Page<Dispute>> myDisputes(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(disputeService.listDisputes(status, page, size));
    }

    public static class CreateDisputeReq {
        public Long orderId;
        public String reason;
        public String evidenceImages;
    }
}
