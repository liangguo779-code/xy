package com.campus.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.entity.User;
import com.campus.common.exception.BusinessException;
import com.campus.common.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sa-Token 路由拦截配置。注册全局登录校验拦截器，
 * 支持通过 yml（sa-token.public-paths / anonymous-paths）扩展放行路径，无需改代码。
 *
 * <p>登录校验之外还做账号状态复核（30 秒缓存）：账号被禁用后已签发 token 立即失效，
 * 不必等到 token 过期。
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final UserMapper userMapper;

    /** 账号状态缓存：userId -> [status, 查询时间戳ms]，30 秒内不重复查库 */
    private final Map<Long, long[]> statusCache = new ConcurrentHashMap<>();

    /** 状态缓存有效期（毫秒） */
    private static final long STATUS_CACHE_TTL_MS = 30_000;

    /** 免登录的公开路径：登录注册、Swagger 文档、WebSocket 握手、上传静态资源等 */
    @Value("${sa-token.public-paths:/api/auth/login,/api/auth/register,/doc.html,/webjars/**,/v3/api-docs/**,/swagger-resources/**,/ws/**,/uploads/**}")
    private String[] publicPaths;

    /** 匿名可访问路径：允许未登录用户试用，若调用需登录的接口仍会在业务层报未登录 */
    @Value("${sa-token.anonymous-paths:/api/ai/**}")
    private String[] anonymousPaths;

    /** 注册拦截器：公开/匿名/internal 路径放行，其余接口强制登录 */
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

                    // 其他接口必须登录，且账号必须仍处于启用状态
                    SaRouter.match("/**").check(r -> {
                        StpUtil.checkLogin();
                        checkUserActive();
                    });
                }))
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }

    /** 账号状态复核：非 status=1 视为不可用，踢掉会话并拒绝请求 */
    private void checkUserActive() {
        Long userId = StpUtil.getLoginIdAsLong();
        long now = System.currentTimeMillis();
        long[] cached = statusCache.get(userId);
        long status;
        if (cached != null && now - cached[1] < STATUS_CACHE_TTL_MS) {
            status = cached[0];
        } else {
            User user = userMapper.selectById(userId);
            status = (user != null && Integer.valueOf(1).equals(user.getStatus())) ? 1 : 0;
            statusCache.put(userId, new long[]{status, now});
        }
        if (status != 1) {
            StpUtil.logout(userId);
            throw new BusinessException(401, "账号已被禁用");
        }
    }
}
