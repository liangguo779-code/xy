package com.campus.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 全局跨域（CORS）配置。注册 CorsFilter 放行所有来源/请求头/方法，
 * 前后端分离开发时前端可携带 Cookie 与凭证访问后端接口。
 */
@Configuration
public class CorsConfig {

    /** 注册全局跨域过滤器：允许任意来源（配合 allowCredentials 使用 * 通配而非具体域名）、预检结果缓存 1 小时 */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // allowedOriginPattern("*") 而非 allowedOrigin("*")：后者与 allowCredentials(true) 互斥
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        // 预检请求（OPTIONS）结果缓存 3600 秒，减少重复预检
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
