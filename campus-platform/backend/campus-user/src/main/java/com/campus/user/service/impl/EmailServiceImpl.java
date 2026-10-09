package com.campus.user.service.impl;

import com.campus.user.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** 邮件发送服务实现，基于 Spring JavaMailSender 投递 HTML 验证码邮件 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    /** 发件人邮箱，取自 spring.mail.username 配置 */
    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * 异步发送验证码邮件（@Async 避免 SMTP 往返阻塞接口调用方）
     * 发送失败抛运行时异常，由调用方兜底转为业务提示
     */
    @Override
    @Async
    public void sendVerificationCode(String toEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("校园生态平台 - 邮箱验证码");

            String html = """
                    <div style="max-width:480px;margin:0 auto;padding:24px;font-family:Arial,sans-serif;">
                        <h2 style="color:#409eff;text-align:center;">校园生态平台</h2>
                        <p>您好，您正在进行邮箱验证，验证码为：</p>
                        <div style="text-align:center;margin:24px 0;">
                            <span style="font-size:32px;font-weight:bold;color:#409eff;letter-spacing:6px;">%s</span>
                        </div>
                        <p style="color:#999;font-size:13px;">验证码 5 分钟内有效，请勿泄露给他人。</p>
                    </div>
                    """.formatted(code);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("验证码邮件已发送: {}", toEmail);
        } catch (Exception e) {
            // MailException(如 SMTP 认证失败)是运行时异常，MessagingException 捕不到，这里兜底全捕
            log.error("发送验证码邮件失败: {}", toEmail, e);
            throw new RuntimeException("邮件发送失败，请稍后重试");
        }
    }
}
