package com.campus.trade.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private Long goodsId;
    private String goodsTitle;
    private String goodsImage;
    private Long buyerId;
    private String buyerNickname;
    private Long sellerId;
    private String sellerNickname;
    private Integer dealType;
    private BigDecimal goodsAmount;
    private BigDecimal serviceFee;
    private String verifyCode;
    private Integer status;
    private String statusDesc;
    private String pickupLocation;
    private LocalDateTime pickupTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
}
