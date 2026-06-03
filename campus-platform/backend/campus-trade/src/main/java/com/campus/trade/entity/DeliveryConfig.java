package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("delivery_config")
public class DeliveryConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private BigDecimal baseFee;
    private BigDecimal perFloorFee;
    private BigDecimal rushHourMultiplier;
    private LocalTime rushHourStart;
    private LocalTime rushHourEnd;
    private BigDecimal maxFee;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
