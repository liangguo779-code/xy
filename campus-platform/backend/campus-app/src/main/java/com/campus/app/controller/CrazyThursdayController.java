package com.campus.app.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.app.service.CrazyThursdayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 疯狂星期四活动接口，提供活动状态查询与用户报名能力 */
@RestController
@RequestMapping("/api/crazy-thursday")
@RequiredArgsConstructor
public class CrazyThursdayController {

    private final CrazyThursdayService crazyThursdayService;

    /**
     * 获取本周活动状态
     *
     * @return 活动信息及当前用户的报名情况（未登录时仅返回活动信息）
     */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        Long userId = null;
        try {
            userId = StpUtil.getLoginIdAsLong();
        } catch (Exception ignored) {
            // 未登录用户也能查看活动状态，只是不带个人报名信息
        }
        return R.ok(crazyThursdayService.getStatus(userId));
    }

    /**
     * 报名本周活动
     *
     * @return 报名结果（重复报名、名额已满等业务失败以 fail 返回）
     */
    @PostMapping("/register")
    public R<Map<String, Object>> register() {
        Long userId = StpUtil.getLoginIdAsLong();
        try {
            return R.ok(crazyThursdayService.register(userId));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }
}
