package com.campus.trade.enums;

import lombok.Getter;

@Getter
public enum DeliveryStatus {

    PENDING(0, "待接单"),
    ACCEPTED(1, "已接单/待取货"),
    PICKED_UP(2, "已取货/配送中"),
    DELIVERED(3, "已送达");

    private final int code;
    private final String desc;

    DeliveryStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
