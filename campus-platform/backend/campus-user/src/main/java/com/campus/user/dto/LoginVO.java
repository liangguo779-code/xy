package com.campus.user.dto;

import com.campus.common.dto.UserVO;
import lombok.Data;

@Data
public class LoginVO {

    private String token;
    private UserVO user;
}
