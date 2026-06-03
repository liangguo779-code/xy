package com.campus.user.dto;

import lombok.Data;

@Data
public class UpdateProfileReq {

    private String nickname;

    private String avatar;

    private String phone;

    private String dormitory;
}
