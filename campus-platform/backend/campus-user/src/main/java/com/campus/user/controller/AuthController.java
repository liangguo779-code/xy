package com.campus.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.user.dto.*;
import com.campus.common.dto.UserVO;
import com.campus.user.service.CaptchaService;
import com.campus.user.service.EmailService;
import com.campus.user.service.UserService;
import cn.hutool.core.util.RandomUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;
    private final CaptchaService captchaService;
    private final StringRedisTemplate redisTemplate;

    private static final String EMAIL_CODE_KEY = "email_code:";
    private static final int CODE_EXPIRE_MINUTES = 5;

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

    /**
     * 获取数学计算验证码
     */
    @GetMapping("/captcha")
    public R<Map<String, String>> getCaptcha() {
        return R.ok(captchaService.generate());
    }

    /**
     * 发送邮箱验证码（注册用）
     * 需要先通过人机验证
     */
    @PostMapping("/send-email-code")
    public R<Void> sendEmailCode(@RequestBody SendEmailCodeReq req) {
        // 1. 校验邮箱格式
        String email = req.getEmail();
        if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            return R.fail(400, "邮箱格式不正确");
        }

        // 2. 校验验证码
        boolean captchaOk = captchaService.verify(req.getCaptchaId(), req.getCaptchaAnswer());
        if (!captchaOk) {
            return R.fail(400, "验证码错误，请重新获取");
        }

        // 3. 检查邮箱是否已注册
        if (userService.getByUsername(email) != null) {
            return R.fail(400, "该邮箱已注册");
        }

        // 4. 生成验证码，存 Redis
        String code = RandomUtil.randomNumbers(6);
        redisTemplate.opsForValue().set(
                EMAIL_CODE_KEY + email,
                code,
                CODE_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 5. 发送邮件
        try {
            emailService.sendVerificationCode(email, code);
        } catch (Exception e) {
            return R.fail(400, "邮件发送失败，请稍后重试");
        }

        return R.ok();
    }

    /**
     * 校验邮箱验证码
     */
    @PostMapping("/verify-email-code")
    public R<Void> verifyEmailCode(@RequestBody VerifyEmailCodeReq req) {
        String cachedCode = redisTemplate.opsForValue().get(EMAIL_CODE_KEY + req.getEmail());
        if (cachedCode == null) {
            return R.fail(400, "验证码已过期，请重新获取");
        }
        if (!cachedCode.equals(req.getCode())) {
            return R.fail(400, "验证码错误");
        }
        return R.ok();
    }

    /** 发送密码重置验证码（邮箱 + 人机验证） */
    @PostMapping("/send-code")
    public R<Void> sendResetCode(@RequestBody SendResetCodeReq req) {
        try {
            // 校验邮箱格式
            String email = req.getEmail();
            if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
                return R.fail(400, "邮箱格式不正确");
            }
            // 校验验证码
            if (!captchaService.verify(req.getCaptchaId(), req.getCaptchaAnswer())) {
                return R.fail(400, "验证码错误，请重新获取");
            }
            userService.sendResetCode(email);
            return R.ok();
        } catch (Exception e) {
            return R.fail(400, e.getMessage() != null ? e.getMessage() : "操作失败");
        }
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

    @Data
    public static class SendEmailCodeReq {
        private String email;
        private String captchaId;
        private String captchaAnswer;
    }

    @Data
    public static class VerifyEmailCodeReq {
        private String email;
        private String code;
    }

    @Data
    public static class BootstrapAdminReq {
        private String username;
        private String password;
    }

    @Data
    public static class SendResetCodeReq {
        private String email;
        private String captchaId;
        private String captchaAnswer;
    }
}
