package com.campus.user.dto;

import lombok.Data;

/** 修改个人信息请求，字段为空表示不修改该项 */
@Data
public class UpdateProfileReq {

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 联系手机号 */
    private String phone;

    /** 宿舍地址 */
    private String dormitory;
}
