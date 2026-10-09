package com.campus.common.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.campus.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sa-Token 鉴权异常处理器。把登录/角色/权限校验失败统一映射为 401/403 语义的 R 结果，
 * 与 GlobalExceptionHandler 分工：本类只处理鉴权类异常。
 */
@Slf4j
@RestControllerAdvice
public class SaTokenExceptionHandler {

    /** 未登录或 token 失效：返回 401，前端应跳转登录页 */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        log.warn("未登录: {}", e.getMessage());
        return R.fail(401, "未登录或登录已过期");
    }

    /** 角色校验不通过：返回 403 并附带缺失的角色名 */
    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException e) {
        log.warn("无角色: {}", e.getRole());
        return R.fail(403, "无此角色权限: " + e.getRole());
    }

    /** 权限校验不通过：返回 403 并附带缺失的权限标识 */
    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermission(NotPermissionException e) {
        log.warn("无权限: {}", e.getPermission());
        return R.fail(403, "无此操作权限: " + e.getPermission());
    }
}
