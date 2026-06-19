package com.campus.feign.trade.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderStatsVO implements Serializable {

    private Long total;
    private Long pending;
    private Long completed;
}
