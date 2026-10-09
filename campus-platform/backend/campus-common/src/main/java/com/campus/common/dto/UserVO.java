package com.campus.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户对外视图对象。相比 User 实体不含密码等敏感字段，
 * 用于个人信息展示、订单/评论中的用户信息等返回场景。
 */
@Data
public class UserVO implements Serializable {

    /** 用户 ID */
    private Long id;
    /** 登录账号 */
    private String username;
    /** 昵称 */
    private String nickname;
    /** 头像 URL */
    private String avatar;
    /** 手机号 */
    private String phone;
    /** 邮箱（可为空） */
    private String email;
    /** 宿舍地址 */
    private String dormitory;
    /** 0-普通用户 1-管理员 2-交付员 */
    private Integer role;
    /** 0-禁用 1-正常 */
    private Integer status;
    /** 注册时间 */
    private LocalDateTime createTime;
}
