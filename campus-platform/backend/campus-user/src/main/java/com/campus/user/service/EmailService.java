package com.campus.user.service;

/** 邮件发送服务，负责投递验证码等通知类邮件 */
public interface EmailService {

    /**
     * 异步发送验证码邮件
     * @param toEmail 收件邮箱
     * @param code 6 位数字验证码
     */
    void sendVerificationCode(String toEmail, String code);
}
