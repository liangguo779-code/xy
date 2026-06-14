package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long goodsId;

    private Long buyerId;

    private Long sellerId;

    /** 0-线下自提 1-平台配送 */
    private Integer dealType;

    /** 商品成交价(线下结算,系统记录) */
    private BigDecimal goodsAmount;

    /** 平台配送服务费 */
    private BigDecimal serviceFee;

    /** 跑腿费付款方: buyer/seller */
    private String deliveryFeePayer;

    /** 楼层（配送时用于计算费用） */
    private Integer floor;

    /** 是否有电梯（配送时用于计算费用） */
    private Integer hasElevator;

    /** 自提核销码 */
    private String verifyCode;

    /** 订单状态 */
    private Integer status;

    private String pickupLocation;

    private LocalDateTime pickupTime;

    private LocalDateTime completeTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
