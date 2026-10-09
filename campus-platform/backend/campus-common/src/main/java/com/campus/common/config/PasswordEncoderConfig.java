package com.campus.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。提供 BCrypt 加密实现，用户密码的加密与校验统一走 PasswordEncoder，
 * 数据库中只存密文，验证时用 matches 明文比对。
 */
@Configuration
public class PasswordEncoderConfig {

    /** 注册 BCrypt 密码编码器（自带随机盐，同明文每次密文不同） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
