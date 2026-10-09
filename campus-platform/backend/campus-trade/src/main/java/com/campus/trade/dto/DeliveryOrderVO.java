package com.campus.trade.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 跑腿配送单视图对象，展示配送进度、凭证照片与跑腿员信息 */
@Data
public class DeliveryOrderVO {
    /** 配送单ID */
    private Long id;
    /** 关联订单ID */
    private Long orderId;
    /** 关联订单编号 */
    private String orderNo;
    /** 跑腿员（交付员）用户ID */
    private Long runnerId;
    /** 跑腿员昵称（冗余填充） */
    private String runnerNickname;
    /** 卖家取货地址 */
    private String sellerAddr;
    /** 买家收货地址 */
    private String buyerAddr;
    /** 取货凭证照片URL */
    private String pickupPhoto;
    /** 送达凭证照片URL */
    private String deliverPhoto;
    /** 配送状态，取值见 DeliveryStatus 枚举 */
    private Integer status;
    /** 配送状态中文描述 */
    private String statusDesc;
    /** 跑腿费（元） */
    private BigDecimal deliveryFee;
    /** 接单时间 */
    private LocalDateTime acceptTime;
    /** 取货时间 */
    private LocalDateTime pickupTime;
    /** 送达时间 */
    private LocalDateTime deliverTime;
    /** 创建时间 */
    private LocalDateTime createTime;
}
