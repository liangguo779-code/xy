package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("delivery_order")
public class DeliveryOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long runnerId;

    private String sellerAddr;

    private String buyerAddr;

    private String pickupPhoto;

    private String deliverPhoto;

    /** 0-待接单 1-待取货 2-配送中 3-已送达 */
    private Integer status;

    /** 楼层 */
    private Integer floor;

    /** 是否有电梯 0-无 1-有 */
    private Integer hasElevator;

    /** 配送费 */
    private BigDecimal deliveryFee;

    private LocalDateTime acceptTime;

    private LocalDateTime pickupTime;

    private LocalDateTime deliverTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
