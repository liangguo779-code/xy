package com.campus.trade.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 热卖票据视图：下单后/确认后返回给前端的票据态对象。
 *
 * <p>订单在 worker 消费前只以 Redis 消息形态存在，没有 DB id——前端一律按 {@code orderNo} 跳
 * {@code /hot-ticket/:orderNo}；{@link #orderId} 仅在已落库（DONE）时回填用于跳订单详情。
 */
@Data
public class HotTicketVO {

    /** 订单号（票据主键） */
    private String orderNo;

    /** 商品 ID */
    private Long goodsId;

    /** 商品标题（冗余填充） */
    private String goodsTitle;

    /** 商品封面图（冗余填充） */
    private String goodsImage;

    /** 购买数量 */
    private Integer qty;

    /** 商品成交金额（元） */
    private BigDecimal goodsAmount;

    /** 平台服务费（元） */
    private BigDecimal serviceFee;

    /** 跑腿费付款方 */
    private String deliveryFeePayer;

    /** 买家用户 ID */
    private Long buyerId;

    /** 买家昵称（冗余填充） */
    private String buyerNickname;

    /** 收货地址快照 */
    private String address;

    /** 票据阶段：PENDING_CONFIRM=待确认 PROCESSING=处理中 DONE=已落库 */
    private String phase;

    /** 阶段中文描述 */
    private String phaseDesc;

    /**
     * 订单状态码（复用 OrderStatus）：待确认=10 处理中=11 已落库=6；
     * 前端 statusType/statusMap 按此渲染，与 DB 订单共用一套色板。
     */
    private Integer status;

    /** 二次确认剩余秒数（仅 PENDING_CONFIRM 有意义，其余为 0） */
    private Long remainSeconds;

    /** 二次确认截止时间戳（epoch 毫秒） */
    private Long expireAt;

    /** 入队时间戳（epoch 毫秒） */
    private Long createdAt;

    /** 落库后的订单 ID（仅 DONE 时非空，用于跳 /orders/:id） */
    private Long orderId;

    /** 票据阶段常量 */
    public static final String PHASE_PENDING_CONFIRM = "PENDING_CONFIRM";
    public static final String PHASE_PROCESSING = "PROCESSING";
    public static final String PHASE_DONE = "DONE";
    public static final String PHASE_CANCELLED = "CANCELLED";
}
