package com.campus.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderReq {

    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    /** 0-自提 1-配送 */
    @NotNull(message = "交易方式不能为空")
    private Integer dealType;

    /** 双方协商的成交价 */
    private BigDecimal agreedPrice;

    /** 自提地点(自提时填写) */
    private String pickupLocation;

    /** 自提时间(自提时填写) */
    private String pickupTime;

    /** 收货地址ID(配送时填写) */
    private Long addressId;

    /** 跑腿费付款方: buyer/seller (配送时填写) */
    private String deliveryFeePayer;

    /** 指定买家ID（仅限聊天确认场景，后端会严格校验） */
    private Long buyerId;
}
