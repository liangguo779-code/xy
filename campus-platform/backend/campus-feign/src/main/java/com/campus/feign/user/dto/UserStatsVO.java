package com.campus.feign.user.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserStatsVO implements Serializable {

    private Long userCount;
    private Long activeCount;
}
