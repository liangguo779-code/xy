package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.DeliveryTrack;

import java.math.BigDecimal;
import java.util.List;

public interface DeliveryTrackService extends IService<DeliveryTrack> {
    /** 添加轨迹记录 */
    void addTrack(Long deliveryId, Long runnerId, String action,
                  BigDecimal lat, BigDecimal lng, String address, String photoUrl);
    /** 获取配送工单的轨迹（需要权限校验） */
    List<DeliveryTrack> getTracks(Long deliveryId, Long userId);
}
