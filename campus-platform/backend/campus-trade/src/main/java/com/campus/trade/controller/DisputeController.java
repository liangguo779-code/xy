package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Dispute;
import com.campus.trade.service.DisputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 纠纷接口：订单纠纷的提交与进度查询 */
@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    /** 发起订单纠纷 */
    @PostMapping
    public R<Void> create(@RequestBody CreateDisputeReq req) {
        disputeService.createDispute(StpUtil.getLoginIdAsLong(), req.orderId, req.reason, req.evidenceImages);
        return R.ok();
    }

    /** 分页查询我的纠纷，可按处理状态过滤 */
    @GetMapping("/my")
    public R<Page<Dispute>> myDisputes(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(disputeService.listDisputes(status, page, size));
    }

    /** 发起纠纷请求体（内联DTO） */
    public static class CreateDisputeReq {
        /** 纠纷所属订单ID */
        public Long orderId;
        /** 纠纷理由 */
        public String reason;
        /** 凭证图片URL（JSON数组字符串），可空 */
        public String evidenceImages;
    }
}
