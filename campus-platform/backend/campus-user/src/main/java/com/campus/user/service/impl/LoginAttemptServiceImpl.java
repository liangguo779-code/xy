package com.campus.user.service.impl;

import com.campus.common.exception.BusinessException;
import com.campus.user.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录防爆破实现：Redis 计数 + 临时锁定。
 *
 * <p>计数窗口 15 分钟（固定窗口，首次失败时设置 TTL）；
 * 锁定 key 独立计时 15 分钟，到期自动解锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    /** 账号失败计数（15 分钟窗口） */
    private static final String FAIL_USER_KEY = "login:fail:user:";
    /** IP 失败计数（15 分钟窗口） */
    private static final String FAIL_IP_KEY = "login:fail:ip:";
    /** 账号锁定标记 */
    private static final String LOCK_USER_KEY = "login:lock:user:";
    /** IP 锁定标记 */
    private static final String LOCK_IP_KEY = "login:lock:ip:";

    /** 账号失败达到该阈值需图形验证码 */
    private static final long CAPTCHA_THRESHOLD = 3;
    /** 账号失败达到该阈值锁定 */
    private static final long USER_LOCK_THRESHOLD = 5;
    /** IP 失败达到该阈值锁定 */
    private static final long IP_LOCK_THRESHOLD = 20;
    /** 计数窗口与锁定时长 */
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    @Override
    public void checkNotLocked(String username, String ip) {
        if (Boolean.TRUE.equals(redis.hasKey(LOCK_USER_KEY + username))
                || Boolean.TRUE.equals(redis.hasKey(LOCK_IP_KEY + ip))) {
            log.warn("登录被锁定拦截: username={}, ip={}", username, ip);
            throw new BusinessException(429, "登录尝试次数过多，请稍后再试");
        }
    }

    @Override
    public boolean captchaRequired(String username) {
        String value = redis.opsForValue().get(FAIL_USER_KEY + username);
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) >= CAPTCHA_THRESHOLD;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void recordFailure(String username, String ip) {
        Long userFails = increment(FAIL_USER_KEY + username);
        Long ipFails = increment(FAIL_IP_KEY + ip);

        if (userFails != null && userFails >= USER_LOCK_THRESHOLD) {
            redis.opsForValue().set(LOCK_USER_KEY + username, "1", WINDOW);
            log.warn("账号因连续登录失败被锁定15分钟: username={}, fails={}", username, userFails);
        }
        if (ipFails != null && ipFails >= IP_LOCK_THRESHOLD) {
            redis.opsForValue().set(LOCK_IP_KEY + ip, "1", WINDOW);
            log.warn("IP 因连续登录失败被锁定15分钟: ip={}, fails={}", ip, ipFails);
        }
    }

    @Override
    public void clearFailures(String username, String ip) {
        redis.delete(FAIL_USER_KEY + username);
        redis.delete(FAIL_IP_KEY + ip);
    }

    /** 计数 +1，首次计数时设置窗口 TTL */
    private Long increment(String key) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, WINDOW);
        }
        return value;
    }
}
