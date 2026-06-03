package com.campus.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 放行登录/注册/公开接口
                    SaRouter.match(
                            "/api/auth/login",
                            "/api/auth/register",
                            "/doc.html",
                            "/webjars/**",
                            "/v3/api-docs/**",
                            "/swagger-resources/**",
                            "/ws/**",
                            "/uploads/**"
                        ).stop();

                    // AI 接口可选鉴权（允许匿名也能用）
                    SaRouter.match("/api/ai/**").stop();

                    // 其他接口必须登录
                    SaRouter.match("/**").check(r -> StpUtil.checkLogin());
                }))
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }
}
