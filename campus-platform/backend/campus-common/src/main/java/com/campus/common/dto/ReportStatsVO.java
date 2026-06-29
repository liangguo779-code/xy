package com.campus.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ReportStatsVO implements Serializable {

    private Long total;
    private Long pending;
}
