package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.dto.DeliveryOrderVO;
import com.campus.trade.entity.DeliveryOrder;

public interface DeliveryService extends IService<DeliveryOrder> {

    /**
     * 创建配送工单(订单选择配送时自动创建)
     */
    DeliveryOrderVO createDeliveryOrder(Long orderId);

    /**
     * 交付员接单
     */
    DeliveryOrderVO acceptOrder(Long runnerId, Long deliveryId);

    /**
     * 交付员取货(上传拍照存证)
     */
    DeliveryOrderVO pickupGoods(Long runnerId, Long deliveryId, String photoUrl);

    /**
     * 交付员送达(上传拍照存证)
     */
    DeliveryOrderVO deliverGoods(Long runnerId, Long deliveryId, String photoUrl);

    /**
     * 获取待接单工单列表
     */
    java.util.List<DeliveryOrderVO> getPendingOrders();

    /**
     * 获取我的工单列表(交付员)
     */
    java.util.List<DeliveryOrderVO> getMyDeliveries(Long runnerId);
}
