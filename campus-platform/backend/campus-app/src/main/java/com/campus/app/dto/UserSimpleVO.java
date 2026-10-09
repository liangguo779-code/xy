package com.campus.app.dto;

import lombok.Data;

import java.io.Serializable;

/** 用户简要信息视图对象，供列表展示等轻量场景使用 */
@Data
public class UserSimpleVO implements Serializable {

    /** 用户ID */
    private Long id;
    /** 昵称 */
    private String nickname;
    /** 头像URL */
    private String avatar;
    /** 0-普通用户 1-管理员 2-跑腿员 */
    private Integer role;
}
