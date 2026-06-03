package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private Integer role;
    private LocalDateTime createTime;
}
