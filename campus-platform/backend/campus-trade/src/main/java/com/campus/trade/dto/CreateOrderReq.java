package com.campus.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 创建订单请求，dealType 决定自提/配送两套字段的取舍 */
@Data
public class CreateOrderReq {

    /** 商品ID */
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

    /** 聊天会话ID（聊天确认场景携带）：库存已由发起交易时占位，建单时校验占位归属并转移给订单，不重复扣减 */
    private Long chatSessionId;

    /** 幂等键（可选）：客户端生成的唯一键，短窗口内重复提交同一键只允许创建一次订单，缺省退化为按商品去重 */
    private String idempotentKey;
}
