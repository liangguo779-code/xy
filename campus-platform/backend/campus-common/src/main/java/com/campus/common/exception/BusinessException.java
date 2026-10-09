package com.campus.common.exception;

import lombok.Getter;

/**
 * 业务异常。业务校验不通过（如余额不足、状态不允许操作）时抛出本异常，
 * 由 GlobalExceptionHandler 捕获后按 code/msg 返回给前端；勿用于系统级未知错误。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码，随异常透传给前端（默认 500） */
    private final int code;

    /** 使用默认业务码 500 构造业务异常 */
    public BusinessException(String message) {
        this(500, message);
    }

    /** 使用指定业务码构造业务异常 */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
