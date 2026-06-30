package com.campus.user.service.impl;

import cn.hutool.core.util.IdUtil;
import com.campus.user.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private final StringRedisTemplate redisTemplate;

    private static final String CAPTCHA_KEY = "captcha:";
    private static final int CAPTCHA_EXPIRE_MINUTES = 5;

    @Override
    public Map<String, String> generate() {
        Random random = new Random();
        int a = random.nextInt(20) + 1;
        int b = random.nextInt(20) + 1;
        String[] ops = {"+", "-", "×"};
        String op = ops[random.nextInt(ops.length)];

        int answer;
        switch (op) {
            case "+": answer = a + b; break;
            case "-": answer = a - b; break;
            default: answer = a * b; break;
        }

        String expression = a + " " + op + " " + b + " = ?";
        String captchaId = IdUtil.fastSimpleUUID();

        // 存 Redis，5 分钟过期
        redisTemplate.opsForValue().set(
                CAPTCHA_KEY + captchaId,
                String.valueOf(answer),
                CAPTCHA_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 生成图片
        String imageBase64 = generateImage(expression);

        Map<String, String> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("image", imageBase64);
        return result;
    }

    @Override
    public boolean verify(String captchaId, String answer) {
        if (captchaId == null || answer == null) return false;

        String cached = redisTemplate.opsForValue().get(CAPTCHA_KEY + captchaId);
        // 用完即删，防止重放
        redisTemplate.delete(CAPTCHA_KEY + captchaId);

        if (cached == null) return false;
        return cached.equals(answer.trim());
    }

    private String generateImage(String expression) {
        int width = 160, height = 50;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 背景
        g.setColor(new Color(245, 245, 245));
        g.fillRect(0, 0, width, height);

        // 干扰线
        Random random = new Random();
        g.setColor(new Color(200, 200, 200));
        for (int i = 0; i < 5; i++) {
            int x1 = random.nextInt(width), y1 = random.nextInt(height);
            int x2 = random.nextInt(width), y2 = random.nextInt(height);
            g.drawLine(x1, y1, x2, y2);
        }

        // 干扰点
        for (int i = 0; i < 30; i++) {
            int x = random.nextInt(width), y = random.nextInt(height);
            g.setColor(new Color(180 + random.nextInt(75), 180 + random.nextInt(75), 180 + random.nextInt(75)));
            g.fillOval(x, y, 2, 2);
        }

        // 文字
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(expression);
        int x = (width - textWidth) / 2;
        int y = (height + fm.getAscent() - fm.getDescent()) / 2;

        // 每个字符随机颜色和微小偏移
        for (int i = 0; i < expression.length(); i++) {
            String ch = String.valueOf(expression.charAt(i));
            g.setColor(new Color(random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            g.drawString(ch, x + random.nextInt(4) - 2, y + random.nextInt(4) - 2);
            x += fm.stringWidth(ch);
        }

        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("验证码图片生成失败", e);
            return "";
        }
    }
}
