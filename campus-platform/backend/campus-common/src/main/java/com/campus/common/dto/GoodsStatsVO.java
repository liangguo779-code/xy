package com.campus.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class GoodsStatsVO implements Serializable {

    private Long total;
    private Long onSale;
    private Long pendingReview;
}
