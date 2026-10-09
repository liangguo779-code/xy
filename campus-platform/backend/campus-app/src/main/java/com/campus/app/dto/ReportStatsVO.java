package com.campus.app.dto;

import lombok.Data;

import java.io.Serializable;

/** 举报统计信息，供管理后台概览展示 */
@Data
public class ReportStatsVO implements Serializable {

    /** 举报总数 */
    private Long total;
    /** 待处理举报数 */
    private Long pending;
}
