package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.entity.DeliveryOrder;
import com.campus.trade.entity.DeliveryTrack;
import com.campus.trade.entity.Order;
import com.campus.trade.mapper.DeliveryOrderMapper;
import com.campus.trade.mapper.DeliveryTrackMapper;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.service.DeliveryTrackService;
import com.campus.common.entity.User;
import com.campus.common.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryTrackServiceImpl extends ServiceImpl<DeliveryTrackMapper, DeliveryTrack> implements DeliveryTrackService {

    private final DeliveryTrackMapper trackMapper;
    private final DeliveryOrderMapper deliveryMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;

    @Override
    public void addTrack(Long deliveryId, Long runnerId, String action,
                         BigDecimal lat, BigDecimal lng, String address, String photoUrl) {
        // 校验是否为该工单的交付员
        DeliveryOrder delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException("工单不存在");
        }
        if (!delivery.getRunnerId().equals(runnerId)) {
            throw new BusinessException(403, "无权操作此工单");
        }

        DeliveryTrack track = new DeliveryTrack();
        track.setDeliveryId(deliveryId);
        track.setRunnerId(runnerId);
        track.setAction(action);
        track.setLatitude(lat);
        track.setLongitude(lng);
        track.setAddress(address);
        track.setPhotoUrl(photoUrl);
        save(track);
    }

    @Override
    public List<DeliveryTrack> getTracks(Long deliveryId, Long userId) {
        // 校验权限：只有买家、卖家、交付员、管理员可以查看轨迹
        DeliveryOrder delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException("工单不存在");
        }

        Order order = orderMapper.selectById(delivery.getOrderId());
        boolean isParticipant = order != null &&
                (order.getBuyerId().equals(userId) || order.getSellerId().equals(userId));
        boolean isRunner = delivery.getRunnerId() != null && delivery.getRunnerId().equals(userId);
        boolean isAdmin = false;
        try {
            User user = userMapper.selectById(userId);
            isAdmin = user != null && Integer.valueOf(1).equals(user.getRole());
        } catch (Exception e) {
            // 查询失败时忽略
        }

        if (!isParticipant && !isRunner && !isAdmin) {
            throw new BusinessException(403, "无权查看此工单轨迹");
        }

        return list(new LambdaQueryWrapper<DeliveryTrack>()
                .eq(DeliveryTrack::getDeliveryId, deliveryId)
                .orderByAsc(DeliveryTrack::getCreateTime));
    }
}
