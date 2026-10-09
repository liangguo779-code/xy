package com.campus.app.dto;

import lombok.Data;

import java.io.Serializable;

/** 商品统计信息，供管理后台概览展示 */
@Data
public class GoodsStatsVO implements Serializable {

    /** 商品总数 */
    private Long total;
    /** 在售商品数 */
    private Long onSale;
    /** 待审核商品数 */
    private Long pendingReview;
}
