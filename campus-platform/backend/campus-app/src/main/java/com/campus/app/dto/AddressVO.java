package com.campus.app.dto;

import lombok.Data;

import java.io.Serializable;

/** 收货地址视图对象，跨模块内部调用时返回地址明细 */
@Data
public class AddressVO implements Serializable {

    /** 地址ID */
    private Long id;
    /** 所属用户ID */
    private Long userId;
    /** 收货人姓名 */
    private String contactName;
    /** 手机号 */
    private String phone;
    /** 宿舍楼栋 */
    private String building;
    /** 宿舍号/详细地址 */
    private String detail;
    /** 是否默认地址 0-否 1-是 */
    private Integer isDefault;
}
