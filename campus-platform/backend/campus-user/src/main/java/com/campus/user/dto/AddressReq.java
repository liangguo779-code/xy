package com.campus.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddressReq {

    private Long id;

    @NotBlank(message = "联系人不能为空")
    private String contactName;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "楼栋不能为空")
    private String building;

    @NotBlank(message = "详细地址不能为空")
    private String detail;

    private Integer isDefault;
}
