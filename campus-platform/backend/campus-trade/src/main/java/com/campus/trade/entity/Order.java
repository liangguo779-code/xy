package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 交易订单，对应 order 表（表名是 SQL 关键字需反引号），自提与配送两条流程共用 */
@Data
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单编号，业务侧唯一单号 */
    private String orderNo;

    /** 商品 ID，关联 goods.id */
    private Long goodsId;

    /** 买家用户 ID */
    private Long buyerId;

    /** 卖家用户 ID */
    private Long sellerId;

    /** 0-线下自提 1-平台配送 */
    private Integer dealType;

    /** 商品成交价(线下结算,系统记录) */
    private BigDecimal goodsAmount;

    /** 平台配送服务费 */
    private BigDecimal serviceFee;

    /** 跑腿费付款方: buyer/seller */
    private String deliveryFeePayer;

    /** 自提核销码 */
    private String verifyCode;

    /** 订单状态：0-待卖家确认 1-已确认/待核销 3-已完成 4-已取消；配送流：5-协商跑腿费中 6-待派单 7-已派单/待取货 8-已取货/配送中 9-已送达/待确认收货；热卖流：10-待用户确认 */
    private Integer status;

    /** 购买数量（热卖用），二手订单固定 1 */
    private Integer quantity;

    /** 冗余商品类型：0-二手 1-热卖，便于订单侧分流不回查商品 */
    private Integer goodsType;

    /** 二次确认截止时间（仅热卖待确认订单）：超时未确认自动取消并释放库存，兜底扫描依据 */
    private LocalDateTime expireTime;

    /** 自提地点 */
    private String pickupLocation;

    /** 自提/取货时间 */
    private LocalDateTime pickupTime;

    /** 订单完成时间 */
    private LocalDateTime completeTime;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
