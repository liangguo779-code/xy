package com.campus.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.R;
import com.campus.user.dto.*;
import com.campus.common.dto.UserVO;
import com.campus.user.service.CaptchaService;
import com.campus.user.service.EmailService;
import com.campus.user.service.UserService;
import cn.hutool.core.util.RandomUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 认证接口：登录/注册/登出、邮箱验证码、密码找回与管理员初始化引导 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;
    private final CaptchaService captchaService;
    private final StringRedisTemplate redisTemplate;

    /** 注册用邮箱验证码的 Redis key 前缀，完整 key 为前缀 + 邮箱 */
    private static final String EMAIL_CODE_KEY = "email_code:";
    /** 邮箱验证码有效期（分钟） */
    private static final int CODE_EXPIRE_MINUTES = 5;

    /** 初始化管理员令牌（环境变量 ADMIN_SETUP_TOKEN）；未配置则 bootstrap 接口关闭 */
    @Value("${app.setup-token:}")
    private String setupToken;

    /** 账号密码登录，成功返回令牌与用户信息；提取 IP/UA 供防爆破与审计 */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginReq req, HttpServletRequest request) {
        return R.ok(userService.login(req, clientIp(request), request.getHeader("User-Agent")));
    }

    /** 提取客户端 IP：优先 X-Forwarded-For 首段（反代场景），否则取直连地址 */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 邮箱注册（username 即邮箱），注册前需已通过邮箱验证码校验 */
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterReq req) {
        userService.register(req);
        return R.ok();
    }

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public R<UserVO> me() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.getCurrentUser(userId));
    }

    /** 退出登录，销毁当前会话 */
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

        // 3.5 发送频控：同邮箱 1 分钟 1 条、每日 10 条，防轰炸邮箱
        String sentKey = "email_code:sent:" + email;
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(sentKey, "1", 1, TimeUnit.MINUTES))) {
            return R.fail(429, "发送过于频繁，请1分钟后再试");
        }
        String dailyKey = "email_code:daily:" + email;
        Long daily = redisTemplate.opsForValue().increment(dailyKey);
        if (daily != null && daily == 1L) {
            redisTemplate.expire(dailyKey, 1, TimeUnit.DAYS);
        }
        if (daily != null && daily > 10) {
            return R.fail(429, "今日发送次数已达上限");
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
            log.error("发送邮箱验证码失败: {}", email, e);
            return R.fail(400, "邮件发送失败，请稍后重试");
        }

        return R.ok();
    }

    /**
     * 校验邮箱验证码（限试错 5 次，常量时间比较）
     */
    @PostMapping("/verify-email-code")
    public R<Void> verifyEmailCode(@RequestBody VerifyEmailCodeReq req) {
        String cachedCode = redisTemplate.opsForValue().get(EMAIL_CODE_KEY + req.getEmail());
        if (cachedCode == null) {
            return R.fail(400, "验证码已过期，请重新获取");
        }
        String attemptsKey = "code_attempts:" + EMAIL_CODE_KEY + req.getEmail();
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts > 5) {
            redisTemplate.delete(EMAIL_CODE_KEY + req.getEmail());
            return R.fail(400, "验证码错误次数过多，请重新获取");
        }
        // 常量时间比较，防时序侧信道
        boolean matched = java.security.MessageDigest.isEqual(
                cachedCode.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                (req.getCode() == null ? "" : req.getCode()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!matched) {
            return R.fail(400, "验证码错误");
        }
        redisTemplate.delete(attemptsKey);
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

    /** 引导设置管理员（仅当系统无管理员时可用）；需持有环境变量下发的初始化令牌 */
    @PostMapping("/bootstrap-admin")
    public R<Void> bootstrapAdmin(@Valid @RequestBody BootstrapAdminReq req) {
        // 令牌未配置则接口整体关闭（默认关门）；比对用常量时间防时序猜测
        if (setupToken == null || setupToken.isBlank()) {
            throw new BusinessException(403, "初始化接口未开放");
        }
        boolean tokenOk = java.security.MessageDigest.isEqual(
                setupToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                (req.getToken() == null ? "" : req.getToken()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!tokenOk) {
            throw new BusinessException(403, "初始化令牌无效");
        }
        userService.bootstrapAdmin(req.getUsername(), req.getPassword());
        return R.ok();
    }

    /** 发送注册邮箱验证码的请求体 */
    @Data
    public static class SendEmailCodeReq {
        /** 目标邮箱 */
        private String email;
        /** 人机验证验证码 ID */
        private String captchaId;
        /** 人机验证答案 */
        private String captchaAnswer;
    }

    /** 校验邮箱验证码的请求体 */
    @Data
    public static class VerifyEmailCodeReq {
        /** 已验证的邮箱 */
        private String email;
        /** 6 位数字验证码 */
        private String code;
    }

    /** 引导设置管理员的请求体 */
    @Data
    public static class BootstrapAdminReq {
        /** 初始化令牌（环境变量 ADMIN_SETUP_TOKEN 下发，未配置则接口关闭） */
        @NotBlank(message = "初始化令牌不能为空")
        private String token;

        /** 管理员登录用户名 */
        @NotBlank(message = "用户名不能为空")
        private String username;

        /** 管理员登录密码，强度高于普通用户（至少 12 位三类字符） */
        @NotBlank(message = "密码不能为空")
        @com.campus.common.validation.PasswordPolicy(min = 12)
        private String password;
    }

    /** 发送密码重置验证码的请求体 */
    @Data
    public static class SendResetCodeReq {
        /** 注册邮箱 */
        private String email;
        /** 人机验证验证码 ID */
        private String captchaId;
        /** 人机验证答案 */
        private String captchaAnswer;
    }
}
