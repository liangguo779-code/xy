package com.campus.trade.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DeliveryOrderVO {
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long runnerId;
    private String runnerNickname;
    private String sellerAddr;
    private String buyerAddr;
    private String pickupPhoto;
    private String deliverPhoto;
    private Integer status;
    private String statusDesc;
    private BigDecimal deliveryFee;
    private LocalDateTime acceptTime;
    private LocalDateTime pickupTime;
    private LocalDateTime deliverTime;
    private LocalDateTime createTime;
}
