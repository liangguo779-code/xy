package com.campus.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserStatsVO implements Serializable {

    private Long userCount;
    private Long activeCount;
}
