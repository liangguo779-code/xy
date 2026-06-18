package com.campus.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Value("${sa-token.public-paths:/api/auth/login,/api/auth/register,/doc.html,/webjars/**,/v3/api-docs/**,/swagger-resources/**,/ws/**,/uploads/**}")
    private String[] publicPaths;

    @Value("${sa-token.anonymous-paths:/api/ai/**}")
    private String[] anonymousPaths;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 放行公开接口（从配置读取）
                    if (publicPaths != null && publicPaths.length > 0) {
                        SaRouter.match(publicPaths).stop();
                    }

                    // 放行匿名接口（从配置读取）
                    if (anonymousPaths != null && anonymousPaths.length > 0) {
                        SaRouter.match(anonymousPaths).stop();
                    }

                    // 内部调用接口放行（Feign 服务间调用）
                    SaRouter.match("/internal/**").stop();

                    // 其他接口必须登录
                    SaRouter.match("/**").check(r -> StpUtil.checkLogin());
                }))
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }
}
