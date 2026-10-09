package com.campus.app.dto;

import lombok.Data;

import java.io.Serializable;

/** 纠纷统计信息，供管理后台概览展示 */
@Data
public class DisputeStatsVO implements Serializable {

    /** 纠纷总数 */
    private Long total;
    /** 待处理纠纷数 */
    private Long pending;
}
