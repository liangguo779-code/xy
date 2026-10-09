package com.campus.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收货地址实体，对应 address 表。一个用户可有多条，其中一条为默认地址，
 * 用于交易下单时选择收货/自提点。
 */
@Data
@TableName("address")
public class Address {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
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

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入/更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
