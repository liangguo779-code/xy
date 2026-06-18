package com.campus.user.feign.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AddressVO implements Serializable {

    private Long id;
    private Long userId;
    private String contactName;
    private String phone;
    private String building;
    private String detail;
    private Integer isDefault;
}
