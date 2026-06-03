package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("address")
public class Address {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 收货人姓名 */
    private String contactName;

    /** 手机号 */
    private String phone;

    /** 宿舍楼栋 */
    private String building;

    /** 宿舍号/详细地址 */
    private String detail;

    /** 是否默认地址 */
    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
