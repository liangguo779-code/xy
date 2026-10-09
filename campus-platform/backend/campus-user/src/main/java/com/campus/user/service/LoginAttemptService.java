package com.campus.user.service;

/**
 * 登录防爆破：基于 Redis 的失败计数与临时锁定。
 *
 * <p>策略：
 * <ul>
 *   <li>同一账号失败 ≥3 次：后续登录需带图形验证码；</li>
 *   <li>同一账号失败 ≥5 次：锁定账号 15 分钟；</li>
 *   <li>同一 IP 失败 ≥20 次：锁定 IP 15 分钟；</li>
 *   <li>登录成功清零对应计数。</li>
 * </ul>
 */
public interface LoginAttemptService {

    /** 是否被锁定（账号或 IP），锁定则应拒绝登录尝试 */
    void checkNotLocked(String username, String ip);

    /** 是否要求图形验证码（账号失败次数达到阈值） */
    boolean captchaRequired(String username);

    /** 记录一次登录失败（含账号与 IP 两个维度计数，达到阈值则锁定） */
    void recordFailure(String username, String ip);

    /** 登录成功后清零计数 */
    void clearFailures(String username, String ip);
}
