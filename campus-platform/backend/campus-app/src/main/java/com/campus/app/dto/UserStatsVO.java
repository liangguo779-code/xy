package com.campus.app.dto;

import lombok.Data;

import java.io.Serializable;

/** 用户统计信息，供管理后台概览展示 */
@Data
public class UserStatsVO implements Serializable {

    /** 用户总数 */
    private Long userCount;
    /** 活跃用户数（status=1 正常状态） */
    private Long activeCount;
}
