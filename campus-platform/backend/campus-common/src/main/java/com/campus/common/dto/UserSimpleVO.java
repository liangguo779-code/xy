package com.campus.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserSimpleVO implements Serializable {

    private Long id;
    private String nickname;
    private String avatar;
    private Integer role;
}
