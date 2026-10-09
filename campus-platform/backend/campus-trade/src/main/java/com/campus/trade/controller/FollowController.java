package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.trade.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 用户关注接口 */
@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /** 关注指定用户 */
    @PostMapping("/{userId}")
    public R<Void> follow(@PathVariable Long userId) {
        followService.follow(StpUtil.getLoginIdAsLong(), userId);
        return R.ok();
    }

    /** 取消关注指定用户 */
    @DeleteMapping("/{userId}")
    public R<Void> unfollow(@PathVariable Long userId) {
        followService.unfollow(StpUtil.getLoginIdAsLong(), userId);
        return R.ok();
    }

    /** 查询我是否已关注该用户 */
    @GetMapping("/check/{userId}")
    public R<Boolean> check(@PathVariable Long userId) {
        return R.ok(followService.isFollowing(StpUtil.getLoginIdAsLong(), userId));
    }

    /** 获取目标用户的关注数与粉丝数 */
    @GetMapping("/count/{userId}")
    public R<Map<String, Long>> count(@PathVariable Long userId) {
        return R.ok(Map.of(
                "following", followService.getFollowingCount(userId),
                "followers", followService.getFollowerCount(userId)
        ));
    }
}
