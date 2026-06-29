package com.campus.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.user.dto.*;
import com.campus.common.dto.UserVO;
import com.campus.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginReq req) {
        return R.ok(userService.login(req));
    }

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterReq req) {
        userService.register(req);
        return R.ok();
    }

    @GetMapping("/me")
    public R<UserVO> me() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.getCurrentUser(userId));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        userService.logout();
        return R.ok();
    }

    /** 发送密码重置验证码 */
    @PostMapping("/send-code")
    public R<Void> sendResetCode(@RequestParam String phone) {
        userService.sendResetCode(phone);
        return R.ok();
    }

    /** 重置密码 */
    @PostMapping("/reset-password")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        userService.resetPassword(req);
        return R.ok();
    }

    /** 引导设置管理员（仅当系统无管理员时可用） */
    @PostMapping("/bootstrap-admin")
    public R<Void> bootstrapAdmin(@RequestBody BootstrapAdminReq req) {
        userService.bootstrapAdmin(req.getUsername(), req.getPassword());
        return R.ok();
    }

    @lombok.Data
    public static class BootstrapAdminReq {
        private String username;
        private String password;
    }
}
