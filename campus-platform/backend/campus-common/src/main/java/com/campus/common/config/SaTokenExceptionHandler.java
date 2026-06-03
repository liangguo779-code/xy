package com.campus.common.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.campus.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        log.warn("未登录: {}", e.getMessage());
        return R.fail(401, "未登录或登录已过期");
    }

    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException e) {
        log.warn("无角色: {}", e.getRole());
        return R.fail(403, "无此角色权限: " + e.getRole());
    }

    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermission(NotPermissionException e) {
        log.warn("无权限: {}", e.getPermission());
        return R.fail(403, "无此操作权限: " + e.getPermission());
    }
}
