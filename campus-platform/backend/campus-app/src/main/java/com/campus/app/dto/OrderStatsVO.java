package com.campus.app.dto;

import lombok.Data;

import java.io.Serializable;

/** 订单统计信息，供管理后台概览展示 */
@Data
public class OrderStatsVO implements Serializable {

    /** 订单总数 */
    private Long total;
    /** 待处理订单数 */
    private Long pending;
    /** 已完成订单数 */
    private Long completed;
}
