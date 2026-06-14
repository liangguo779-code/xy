package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trade.entity.DeliveryConfig;
import com.campus.trade.mapper.DeliveryConfigMapper;
import com.campus.trade.service.DeliveryFeeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

@Service
public class DeliveryFeeServiceImpl extends ServiceImpl<DeliveryConfigMapper, DeliveryConfig> implements DeliveryFeeService {

    @Override
    public BigDecimal calculateFee(int floor, boolean hasElevator) {
        DeliveryConfig config = getConfig();
        BigDecimal fee = config.getBaseFee();

        // 无电梯楼层加价
        if (!hasElevator && floor > 1) {
            fee = fee.add(config.getPerFloorFee().multiply(BigDecimal.valueOf(floor - 1)));
        }

        // 高峰时段加价
        LocalTime now = LocalTime.now();
        if (now.isAfter(config.getRushHourStart()) && now.isBefore(config.getRushHourEnd())) {
            fee = fee.multiply(config.getRushHourMultiplier());
        }

        // 封顶
        if (fee.compareTo(config.getMaxFee()) > 0) {
            fee = config.getMaxFee();
        }

        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public DeliveryConfig getConfig() {
        // 显式取第一条记录，避免多条记录时行为不确定
        DeliveryConfig config = getOne(new LambdaQueryWrapper<DeliveryConfig>()
                .orderByAsc(DeliveryConfig::getId).last("LIMIT 1"));
        if (config == null) {
            // 返回默认配置
            config = new DeliveryConfig();
            config.setBaseFee(new BigDecimal("5.00"));
            config.setPerFloorFee(new BigDecimal("1.00"));
            config.setRushHourMultiplier(new BigDecimal("1.50"));
            config.setRushHourStart(LocalTime.of(11, 30));
            config.setRushHourEnd(LocalTime.of(13, 30));
            config.setMaxFee(new BigDecimal("15.00"));
        }
        return config;
    }

    @Override
    public void updateConfig(DeliveryConfig config) {
        DeliveryConfig existing = getConfig();
        if (existing.getId() == null) {
            // 数据库无配置记录，执行插入
            save(config);
        } else {
            config.setId(existing.getId());
            updateById(config);
        }
    }
}
