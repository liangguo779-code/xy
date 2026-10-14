package com.campus.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 热卖商品下单请求：无协商环节，下单即扣库存，等待买家二次确认 */
@Data
public class CreateHotOrderReq {

    /** 商品ID（热卖商品） */
    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    /** 购买数量，最少 1 */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;

    /** 收货地址ID，缺省用买家默认地址 */
    private Long addressId;

    /** 跑腿费付款方: buyer/seller，缺省 buyer */
    private String deliveryFeePayer;

    /** 幂等键（可选）：客户端生成的唯一键，短窗口内重复提交同一键只允许创建一次订单 */
    private String idempotentKey;
}
