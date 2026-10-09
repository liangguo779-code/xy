package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 跑腿配送单，对应 delivery_order 表，校内代取/代送订单的配送履约记录 */
@Data
@TableName("delivery_order")
public class DeliveryOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的交易订单 ID */
    private Long orderId;

    /** 接单跑腿员（骑手）用户 ID */
    private Long runnerId;

    /** 取货地址（卖家/商家位置） */
    private String sellerAddr;

    /** 收货地址（买家位置） */
    private String buyerAddr;

    /** 取货凭证照片 URL */
    private String pickupPhoto;

    /** 送达凭证照片 URL */
    private String deliverPhoto;

    /** 0-待接单 1-待取货 2-配送中 3-已送达 */
    private Integer status;

    /** 配送费 */
    private BigDecimal deliveryFee;

    /** 接单时间 */
    private LocalDateTime acceptTime;

    /** 取货时间 */
    private LocalDateTime pickupTime;

    /** 送达时间 */
    private LocalDateTime deliverTime;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
