package com.campus.common.exception;

import com.campus.common.result.R;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。把 Controller 抛出的异常统一转换为 {@link R} 返回，
 * 保证前端拿到的失败响应结构一致（code + msg），避免异常堆栈泄露。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 处理业务异常：保留原始业务码与提示信息（warn 级别，属可预期失败） */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 处理 @RequestBody 参数校验失败：取第一个字段错误拼成 "字段: 原因" 返回 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return R.fail(400, msg);
    }

    /** 处理表单/查询参数绑定校验失败，返回格式同上 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数绑定失败");
        return R.fail(400, msg);
    }

    /** 处理方法级校验注解（如 @Min 作用于普通参数）抛出的约束违例 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraint(ConstraintViolationException e) {
        return R.fail(400, e.getMessage());
    }

    /** 兜底处理未知系统异常：记录 error 日志便于排查，msg 为空时给出通用提示 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        String msg = e.getMessage();
        return R.fail(500, msg != null && !msg.isEmpty() ? msg : "系统内部错误");
    }
}
