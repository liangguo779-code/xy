package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.service.BlockService;
import com.campus.common.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/{userId}")
    public R<Void> blockUser(@PathVariable Long userId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        blockService.blockUser(currentUserId, userId);
        return R.ok();
    }

    @DeleteMapping("/{userId}")
    public R<Void> unblockUser(@PathVariable Long userId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        blockService.unblockUser(currentUserId, userId);
        return R.ok();
    }

    @GetMapping("/check/{userId}")
    public R<Map<String, Boolean>> checkBlock(@PathVariable Long userId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        return R.ok(Map.of(
                "blocked", blockService.isBlocked(currentUserId, userId),
                "blockedBy", blockService.isBlockedBy(currentUserId, userId)
        ));
    }

    @GetMapping
    public R<Page<UserVO>> getBlockList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        return R.ok(blockService.getBlockList(currentUserId, page, size));
    }
}
