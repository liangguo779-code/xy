package com.campus.trade.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Dispute;
import com.campus.trade.service.DisputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理端纠纷接口：查看纠纷列表并裁决（需 ADMIN 角色） */
@RestController
@RequestMapping("/api/admin/disputes")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final DisputeService disputeService;

    /** 分页查询纠纷列表，可按处理状态过滤 */
    @GetMapping
    public R<Page<Dispute>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(disputeService.listDisputes(status, page, size));
    }

    /** 裁决纠纷：填写处理结论并更新状态 */
    @PutMapping("/{id}/resolve")
    public R<Void> resolve(@PathVariable Long id, @RequestBody ResolveReq req) {
        disputeService.resolveDispute(id, StpUtil.getLoginIdAsLong(), req.result, req.status);
        return R.ok();
    }

    /** 纠纷裁决请求体（内联DTO） */
    public static class ResolveReq {
        /** 裁决结论说明 */
        public String result;
        /** 更新后的纠纷状态 */
        public int status;
    }
}
