package com.campus.feign.trade.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DisputeStatsVO implements Serializable {

    private Long total;
    private Long pending;
}
