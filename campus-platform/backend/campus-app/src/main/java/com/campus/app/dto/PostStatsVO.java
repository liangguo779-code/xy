package com.campus.app.dto;

import lombok.Data;

import java.io.Serializable;

/** 帖子统计信息，供管理后台概览展示 */
@Data
public class PostStatsVO implements Serializable {

    /** 帖子总数 */
    private Long total;
    /** 活跃帖子数（status=1 正常展示中） */
    private Long active;
}
