package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.service.BlockService;
import com.campus.common.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 黑名单接口：拉黑/取消拉黑用户、查询拉黑关系与列表 */
@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    /** 拉黑指定用户 */
    @PostMapping("/{userId}")
    public R<Void> blockUser(@PathVariable Long userId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        blockService.blockUser(currentUserId, userId);
        return R.ok();
    }

    /** 取消拉黑指定用户 */
    @DeleteMapping("/{userId}")
    public R<Void> unblockUser(@PathVariable Long userId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        blockService.unblockUser(currentUserId, userId);
        return R.ok();
    }

    /** 查询当前用户与目标用户的双向拉黑关系（blocked-我拉黑了对方 blockedBy-对方拉黑了我） */
    @GetMapping("/check/{userId}")
    public R<Map<String, Boolean>> checkBlock(@PathVariable Long userId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        return R.ok(Map.of(
                "blocked", blockService.isBlocked(currentUserId, userId),
                "blockedBy", blockService.isBlockedBy(currentUserId, userId)
        ));
    }

    /** 分页获取我的黑名单 */
    @GetMapping
    public R<Page<UserVO>> getBlockList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        return R.ok(blockService.getBlockList(currentUserId, page, size));
    }
}
