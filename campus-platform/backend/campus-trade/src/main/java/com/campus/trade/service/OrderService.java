package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.dto.CreateOrderReq;
import com.campus.trade.dto.CreateReviewReq;
import com.campus.trade.dto.OrderVO;
import com.campus.trade.entity.Order;

import java.util.List;

public interface OrderService extends IService<Order> {

    /**
     * 发起交易(自提或配送)
     * 买家在聊天中点击"发起平台配送"或"约定自提"
     */
    OrderVO createOrder(Long buyerId, CreateOrderReq req);

    /**
     * 卖家确认订单(自提流程)
     * 确认后生成核销码
     */
    OrderVO confirmOrder(Long sellerId, Long orderId);

    /**
     * 卖家输入核销码完成自提交易
     * 或卖家点击"我已卖出"
     */
    OrderVO completeSelfPickup(Long sellerId, Long orderId, String verifyCode);

    /**
     * 买家支付配送服务费（已废弃，支付改为线下）
     */
    OrderVO payDeliveryFee(Long buyerId, Long orderId);

    /**
     * 确认配送安排（聊天中协商后确认）
     * 确认后自动创建配送工单，推送到派单大厅
     */
    OrderVO confirmDelivery(Long userId, Long orderId, String deliveryFeePayer);

    /**
     * 买家确认收货(配送流程)
     */
    OrderVO confirmReceive(Long buyerId, Long orderId);

    /**
     * 取消订单
     */
    OrderVO cancelOrder(Long userId, Long orderId);

    /**
     * 评价交易
     */
    void createReview(Long reviewerId, CreateReviewReq req);

    /**
     * 获取我的订单列表
     */
    List<OrderVO> getMyOrders(Long userId, Integer status);

    /**
     * 获取订单详情
     */
    OrderVO getOrderDetail(Long userId, Long orderId);
}
