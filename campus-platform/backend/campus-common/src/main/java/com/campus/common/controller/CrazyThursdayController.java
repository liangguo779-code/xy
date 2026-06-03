package com.campus.common.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.common.service.CrazyThursdayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/crazy-thursday")
@RequiredArgsConstructor
public class CrazyThursdayController {

    private final CrazyThursdayService crazyThursdayService;

    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        Long userId = null;
        try {
            userId = StpUtil.getLoginIdAsLong();
        } catch (Exception ignored) {
        }
        return R.ok(crazyThursdayService.getStatus(userId));
    }

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
