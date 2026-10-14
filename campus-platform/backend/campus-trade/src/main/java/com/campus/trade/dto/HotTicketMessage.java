package com.campus.trade.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 热卖票据消息：Redis list 成员的自包含载荷（JSON 字符串）。
 *
 * <p>下单时由 JVM 构造、经 Lua RPUSH 进待确认桶；消费时凭它落库，不再回查下单请求。
 * 金额字段用字符串序列化（经 cjson 往返安全），时间戳用 epoch 毫秒（无时区/格式歧义）。
 */
@Data
public class HotTicketMessage {

    /** 订单号（JVM 生成，经 ARGV 传给 Lua） */
    private String orderNo;

    /** 买家用户 ID */
    private Long buyerId;

    /** 买家昵称（展示用冗余） */
    private String buyerNickname;

    /** 商品 ID */
    private Long goodsId;

    /** 商品标题（展示用冗余） */
    private String goodsTitle;

    /** 商品封面图（展示用冗余） */
    private String goodsImage;

    /** 卖家用户 ID（热卖为发布管理员） */
    private Long sellerId;

    /** 购买数量 */
    private Integer qty;

    /** 商品成交金额（元）——字符串序列化，避免 cjson 浮点精度漂移 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal goodsAmount;

    /** 平台服务费（元）——同上字符串序列化 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal serviceFee;

    /** 跑腿费付款方: buyer/seller */
    private String deliveryFeePayer;

    /** 收货地址快照（落库写入 order.pickupLocation） */
    private String address;

    /** 入队时间戳（epoch 毫秒） */
    private Long createdAt;

    /** 二次确认截止时间戳（epoch 毫秒） */
    private Long expireAt;
}
