package com.campus.trade.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单视图对象，列表/详情通用，附带商品与买卖双方的冗余展示字段 */
@Data
public class OrderVO {
    /** 订单ID */
    private Long id;
    /** 订单编号（业务展示用） */
    private String orderNo;
    /** 商品ID */
    private Long goodsId;
    /** 商品标题（冗余填充） */
    private String goodsTitle;
    /** 商品封面图URL（冗余填充） */
    private String goodsImage;
    /** 买家用户ID */
    private Long buyerId;
    /** 买家昵称（冗余填充） */
    private String buyerNickname;
    /** 卖家用户ID */
    private Long sellerId;
    /** 卖家昵称（冗余填充） */
    private String sellerNickname;
    /** 0-自提 1-配送 */
    private Integer dealType;
    /** 商品成交金额（元） */
    private BigDecimal goodsAmount;
    /** 平台服务费（元） */
    private BigDecimal serviceFee;
    /** 核销码，自提订单买家出示、卖家核销时使用 */
    private String verifyCode;
    /** 订单状态，取值见 OrderStatus 枚举 */
    private Integer status;
    /** 订单状态中文描述 */
    private String statusDesc;
    /** 配送单ID，配送订单才有 */
    private Long deliveryOrderId;
    /** 自提地点 */
    private String pickupLocation;
    /** 自提时间 */
    private LocalDateTime pickupTime;
    /** 完成时间 */
    private LocalDateTime completeTime;
    /** 下单时间 */
    private LocalDateTime createTime;
    /** 购买数量（热卖用），二手订单为 1 */
    private Integer quantity;
    /** 二次确认截止时间（仅热卖待确认订单），前端据此渲染倒计时 */
    private LocalDateTime expireTime;
    /** 商品类型 0-二手 1-热卖，前端据此切换下单/确认购买交互 */
    private Integer goodsType;
}
