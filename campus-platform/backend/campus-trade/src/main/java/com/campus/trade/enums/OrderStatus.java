package com.campus.trade.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {

    // 自提流程
    PENDING(0, "待卖家确认"),
    CONFIRMED(1, "已确认/待核销"),

    // 配送流程
    DELIVERY_NEGOTIATING(5, "协商跑腿费中"),
    PENDING_DELIVERY(6, "待派单"),
    ASSIGNED(7, "已派单/待取货"),
    PICKED_UP(8, "已取货/配送中"),
    DELIVERED(9, "已送达/待确认收货"),

    // 通用终态
    COMPLETED(3, "已完成"),
    CANCELLED(4, "已取消");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus fromCode(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }
}
