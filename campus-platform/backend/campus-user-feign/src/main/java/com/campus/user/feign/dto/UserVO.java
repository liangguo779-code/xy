package com.campus.user.feign.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserVO implements Serializable {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private String dormitory;
    private Integer role;
    private Integer status;
    private LocalDateTime createTime;
}
