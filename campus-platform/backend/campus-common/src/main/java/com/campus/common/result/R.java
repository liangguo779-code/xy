package com.campus.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一接口返回结果包装类。
 * 所有 Controller 对外返回均应使用本类包装，前端按 code 判断业务成败（200-成功，其余为失败），
 * msg 为提示信息，data 为业务数据（可为 null）。
 */
@Data
public class R<T> implements Serializable {

    /** 业务状态码：200-成功，400-参数错误，500-业务/系统失败，其余见各业务约定 */
    private int code;
    /** 提示信息，失败时给前端展示的原因 */
    private String msg;
    /** 业务数据，成功时返回，失败时为 null */
    private T data;

    /** 构造无 data 的成功结果 */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /** 构造携带 data 的成功结果（code=200） */
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMsg("success");
        r.setData(data);
        return r;
    }

    /** 构造 code=500 的失败结果 */
    public static <T> R<T> fail(String msg) {
        return fail(500, msg);
    }

    /** 构造指定业务码的失败结果 */
    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
