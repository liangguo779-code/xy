package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.DeliveryConfig;

import java.math.BigDecimal;

public interface DeliveryFeeService extends IService<DeliveryConfig> {
    /** 计算配送服务费 */
    BigDecimal calculateFee(int floor, boolean hasElevator);
    /** 获取当前配置 */
    DeliveryConfig getConfig();
    /** 更新配置 */
    void updateConfig(DeliveryConfig config);
}
