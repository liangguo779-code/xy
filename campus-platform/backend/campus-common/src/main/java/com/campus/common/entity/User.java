package com.campus.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体，对应 user 表。全平台共用（买家/卖家/管理员/交付员），
 * 密码为 BCrypt 密文，对外输出请使用 UserVO 避免泄露。
 */
@Data
@TableName("user")
public class User {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（唯一） */
    private String username;

    /** 登录密码（BCrypt 密文，禁止返回给前端） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 宿舍地址 */
    private String dormitory;

    /** 0-普通用户 1-管理员 2-交付员 */
    private Integer role;

    /** 0-禁用 1-正常 */
    private Integer status;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入/更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
