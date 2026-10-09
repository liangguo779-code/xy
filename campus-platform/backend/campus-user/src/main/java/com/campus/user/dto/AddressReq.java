package com.campus.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 收货地址新增/修改请求，新增时 id 为空、修改时必填 */
@Data
public class AddressReq {

    /** 地址 ID，修改时必填（用于定位记录与校验归属） */
    private Long id;

    /** 联系人姓名，收货人 */
    @NotBlank(message = "联系人姓名不能为空")
    private String contactName;

    /** 联系手机号 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 楼栋，如 "9号楼" */
    @NotBlank(message = "楼栋不能为空")
    private String building;

    /** 详细地址，如宿舍房间号 */
    @NotBlank(message = "详细地址不能为空")
    private String detail;

    /** 是否默认地址：0-否 1-是，为空时按 0 处理 */
    private Integer isDefault;
}
