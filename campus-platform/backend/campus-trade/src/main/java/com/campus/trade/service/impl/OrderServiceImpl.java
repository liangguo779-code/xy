package com.campus.trade.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.entity.BanRecord;
import com.campus.common.exception.BusinessException;
import com.campus.common.service.BanService;
import com.campus.trade.dto.CreateOrderReq;
import com.campus.trade.dto.CreateReviewReq;
import com.campus.trade.dto.OrderVO;
import com.campus.trade.entity.*;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.*;
import com.campus.trade.service.DeliveryService;
import com.campus.trade.service.OrderService;
import com.campus.trade.websocket.ChatWebSocketHandler;
import com.campus.user.entity.Address;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final ReviewMapper reviewMapper;
    private final DeliveryService deliveryService;
    private final AddressService addressService;
    private final ChatWebSocketHandler wsHandler;
    private final BanService banService;

    @Value("${delivery.service-fee:5.00}")
    private BigDecimal defaultServiceFee;

    @Override
    @Transactional
    public OrderVO createOrder(Long buyerId, CreateOrderReq req) {
        // 检查买家交易封禁
        BanRecord buyerBan = banService.checkUserBan(buyerId, "trade");
        if (buyerBan != null) {
            throw new BusinessException("您的交易功能已被封禁，原因：" + buyerBan.getReason()
                    + "，解封时间：" + buyerBan.getBanUntil());
        }

        Goods goods = goodsMapper.selectById(req.getGoodsId());
        if (goods == null || goods.getStatus() != 0) {
            throw new BusinessException("商品不存在或已下架");
        }

        // 防重复下单：检查该商品是否已有未完成订单
        Long existingOrder = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getGoodsId, goods.getId())
                        .ne(Order::getStatus, OrderStatus.COMPLETED.getCode())
                        .ne(Order::getStatus, OrderStatus.CANCELLED.getCode()));
        if (existingOrder > 0) {
            throw new BusinessException("该商品已有进行中的订单");
        }

        // 确定买家：优先使用请求中指定的buyerId（配送确认场景），否则为当前用户
        Long actualBuyerId = (req.getBuyerId() != null) ? req.getBuyerId() : buyerId;
        Long sellerId = goods.getUserId();

        // 如果买家和卖家是同一人，说明发起方就是卖家，此时确认方应为买家
        if (actualBuyerId.equals(sellerId)) {
            // 当前确认者是买家（不是卖家）
            actualBuyerId = buyerId;
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setGoodsId(goods.getId());
        order.setBuyerId(actualBuyerId);
        order.setSellerId(sellerId);
        order.setDealType(req.getDealType());
        order.setGoodsAmount(req.getAgreedPrice() != null ? req.getAgreedPrice() : goods.getPrice());

        if (req.getDealType() == 0) {
            // 自提流程：双方已确认，直接进入待核销状态
            order.setStatus(OrderStatus.CONFIRMED.getCode());
            order.setVerifyCode(RandomUtil.randomString(6).toUpperCase());
            order.setPickupLocation(req.getPickupLocation());
            order.setPickupTime(req.getPickupTime() != null
                    ? LocalDateTime.parse(req.getPickupTime()) : null);
        } else {
            // 配送流程：校验双方地址
            Address buyerAddr = req.getAddressId() != null
                    ? addressService.getAddress(actualBuyerId, req.getAddressId())
                    : addressService.getDefaultAddress(actualBuyerId);
            if (buyerAddr == null) {
                throw new BusinessException("买家尚未添加收货地址，请先在收货地址中添加");
            }

            Address sellerAddr = addressService.getDefaultAddress(sellerId);
            if (sellerAddr == null) {
                throw new BusinessException("卖家尚未添加收货地址，无法发起配送");
            }

            order.setStatus(OrderStatus.DELIVERY_NEGOTIATING.getCode());
            order.setServiceFee(defaultServiceFee);
            order.setDeliveryFeePayer(req.getDeliveryFeePayer() != null ? req.getDeliveryFeePayer() : "buyer");
        }

        orderMapper.insert(order);

        // 通知对方
        notifyUser(goods.getUserId(), "new_order", order);

        // 配送订单不立即推送到派单大厅，等对方确认后再推送

        log.info("订单创建: orderNo={}, dealType={}", order.getOrderNo(), req.getDealType());
        return getOrderDetail(buyerId, order.getId());
    }

    @Override
    @Transactional
    public OrderVO confirmOrder(Long sellerId, Long orderId) {
        Order order = getOrderById(orderId);
        if (!order.getSellerId().equals(sellerId)) {
            throw new BusinessException(403, "无权操作");
        }
        if (order.getStatus() != OrderStatus.PENDING.getCode()) {
            throw new BusinessException("订单状态不正确");
        }

        order.setStatus(OrderStatus.CONFIRMED.getCode());
        orderMapper.updateById(order);

        // 通知买家：卖家已确认，可凭核销码自提
        notifyUser(order.getBuyerId(), "order_confirmed", Map.of(
                "orderId", order.getId(),
                "verifyCode", order.getVerifyCode(),
                "msg", "卖家已确认交易，请前往约定地点自提。核销码: " + order.getVerifyCode()
        ));

        return getOrderDetail(sellerId, orderId);
    }

    @Override
    @Transactional
    public OrderVO completeSelfPickup(Long sellerId, Long orderId, String verifyCode) {
        Order order = getOrderById(orderId);
        if (!order.getSellerId().equals(sellerId)) {
            throw new BusinessException(403, "无权操作");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED.getCode()) {
            throw new BusinessException("订单状态不正确");
        }
        // 核销码校验：必须提供且正确
        if (!StringUtils.hasText(verifyCode) || !verifyCode.equalsIgnoreCase(order.getVerifyCode())) {
            throw new BusinessException("核销码错误，请向买家索取正确的核销码");
        }

        // 完成交易
        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setCompleteTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 商品标记已售出
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, order.getGoodsId())
                .set(Goods::getStatus, 2));

        // 双方弹评价邀请
        notifyUser(order.getBuyerId(), "review_invite", Map.of(
                "orderId", order.getId(),
                "msg", "交易完成！请对卖家进行评价"
        ));
        notifyUser(order.getSellerId(), "review_invite", Map.of(
                "orderId", order.getId(),
                "msg", "交易完成！请对买家进行评价"
        ));

        log.info("自提交易完成: orderNo={}", order.getOrderNo());
        return getOrderDetail(sellerId, orderId);
    }

    @Override
    @Transactional
    public OrderVO payDeliveryFee(Long buyerId, Long orderId) {
        // 支付已改为线下，此方法保留兼容性
        Order order = getOrderById(orderId);
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(403, "无权操作");
        }
        // 支付已改为线下，直接返回当前订单状态
        return getOrderDetail(buyerId, orderId);
    }

    @Override
    @Transactional
    public OrderVO confirmDelivery(Long userId, Long orderId, String deliveryFeePayer) {
        Order order = getOrderById(orderId);
        // 买家或卖家都可以确认
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(403, "无权操作");
        }
        if (order.getStatus() != OrderStatus.DELIVERY_NEGOTIATING.getCode()) {
            throw new BusinessException("订单状态不正确");
        }

        // 更新跑腿费付款方
        order.setDeliveryFeePayer(deliveryFeePayer);
        order.setStatus(OrderStatus.PENDING_DELIVERY.getCode());
        orderMapper.updateById(order);

        // 自动创建配送工单，推送到派单大厅
        deliveryService.createDeliveryOrder(orderId);

        // 通知双方
        String payerName = "buyer".equals(deliveryFeePayer) ? "买家" : "卖家";
        notifyUser(order.getBuyerId(), "delivery_confirmed", Map.of(
                "orderId", order.getId(),
                "msg", "配送已确认，跑腿费由" + payerName + "承担，等待骑手接单"
        ));
        notifyUser(order.getSellerId(), "delivery_confirmed", Map.of(
                "orderId", order.getId(),
                "msg", "配送已确认，跑腿费由" + payerName + "承担，等待骑手接单"
        ));

        log.info("配送确认: orderId={}, payer={}", orderId, deliveryFeePayer);
        return getOrderDetail(userId, orderId);
    }

    @Override
    @Transactional
    public OrderVO confirmReceive(Long buyerId, Long orderId) {
        Order order = getOrderById(orderId);
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(403, "无权操作");
        }
        if (order.getStatus() != OrderStatus.DELIVERED.getCode()) {
            throw new BusinessException("订单状态不正确，等待交付员送达后再确认");
        }

        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setCompleteTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 商品标记已售出
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, order.getGoodsId())
                .set(Goods::getStatus, 2));

        // 双方弹评价邀请
        notifyUser(order.getBuyerId(), "review_invite", Map.of(
                "orderId", order.getId(), "msg", "交易完成！请对卖家和交付员进行评价"
        ));
        notifyUser(order.getSellerId(), "review_invite", Map.of(
                "orderId", order.getId(), "msg", "交易完成！请对买家进行评价"
        ));

        log.info("配送交易完成: orderNo={}", order.getOrderNo());
        return getOrderDetail(buyerId, orderId);
    }

    @Override
    @Transactional
    public OrderVO cancelOrder(Long userId, Long orderId) {
        Order order = getOrderById(orderId);
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(403, "无权操作");
        }
        if (order.getStatus() == OrderStatus.COMPLETED.getCode()
                || order.getStatus() == OrderStatus.CANCELLED.getCode()) {
            throw new BusinessException("订单已完成或已取消");
        }
        // 已取货后不能取消
        if (order.getStatus() >= OrderStatus.PICKED_UP.getCode()) {
            throw new BusinessException("配送中无法取消，请联系客服");
        }

        order.setStatus(OrderStatus.CANCELLED.getCode());
        orderMapper.updateById(order);

        Long otherUserId = order.getBuyerId().equals(userId)
                ? order.getSellerId() : order.getBuyerId();
        notifyUser(otherUserId, "order_cancelled", Map.of(
                "orderId", order.getId(),
                "msg", "对方已取消订单"
        ));

        return getOrderDetail(userId, orderId);
    }

    @Override
    @Transactional
    public void createReview(Long reviewerId, CreateReviewReq req) {
        Order order = getOrderById(req.getOrderId());
        if (order.getStatus() != OrderStatus.COMPLETED.getCode()) {
            throw new BusinessException("订单未完成，不能评价");
        }
        if (!order.getBuyerId().equals(reviewerId) && !order.getSellerId().equals(reviewerId)) {
            throw new BusinessException(403, "无权操作");
        }

        Long targetId = order.getBuyerId().equals(reviewerId)
                ? order.getSellerId() : order.getBuyerId();

        // 检查是否已评价
        Long count = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderId, req.getOrderId())
                        .eq(Review::getReviewerId, reviewerId)
        );
        if (count > 0) {
            throw new BusinessException("已评价，不能重复评价");
        }

        Review review = new Review();
        review.setOrderId(req.getOrderId());
        review.setReviewerId(reviewerId);
        review.setTargetId(targetId);
        review.setRating(req.getRating());
        review.setContent(req.getContent());
        reviewMapper.insert(review);
    }

    @Override
    public List<OrderVO> getMyOrders(Long userId, Integer status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .and(w -> w.eq(Order::getBuyerId, userId).or().eq(Order::getSellerId, userId))
                .orderByDesc(Order::getCreateTime);
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        List<Order> orders = orderMapper.selectList(wrapper);
        return orders.stream().map(o -> toVO(o, userId)).collect(Collectors.toList());
    }

    @Override
    public OrderVO getOrderDetail(Long userId, Long orderId) {
        Order order = getOrderById(orderId);
        // 只有买家、卖家、管理员可以查看订单详情
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            // 检查是否是管理员
            User user = userMapper.selectById(userId);
            if (user == null || user.getRole() != 1) {
                throw new BusinessException(403, "无权查看此订单");
            }
        }
        return toVO(order, userId);
    }

    // ============ private helpers ============

    private Order getOrderById(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        return order;
    }

    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + RandomUtil.randomString(4).toUpperCase();
    }

    private void notifyUser(Long userId, String type, Object data) {
        wsHandler.pushToUser(userId, Map.of("type", type, "data", data));
    }

    private OrderVO toVO(Order order, Long currentUserId) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setGoodsId(order.getGoodsId());
        vo.setBuyerId(order.getBuyerId());
        vo.setSellerId(order.getSellerId());
        vo.setDealType(order.getDealType());
        vo.setGoodsAmount(order.getGoodsAmount());
        vo.setServiceFee(order.getServiceFee());
        vo.setVerifyCode(
                order.getBuyerId().equals(currentUserId) ? order.getVerifyCode() : null
        );
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.fromCode(order.getStatus()).getDesc());
        vo.setPickupLocation(order.getPickupLocation());
        vo.setPickupTime(order.getPickupTime());
        vo.setCompleteTime(order.getCompleteTime());
        vo.setCreateTime(order.getCreateTime());

        // 填充商品和用户信息
        Goods goods = goodsMapper.selectById(order.getGoodsId());
        if (goods != null) {
            vo.setGoodsTitle(goods.getTitle());
            try {
                List<String> images = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(goods.getImages(), List.class);
                vo.setGoodsImage(images.isEmpty() ? null : images.get(0));
            } catch (Exception e) {
                vo.setGoodsImage(null);
            }
        }
        User buyer = userMapper.selectById(order.getBuyerId());
        if (buyer != null) vo.setBuyerNickname(buyer.getNickname());
        User seller = userMapper.selectById(order.getSellerId());
        if (seller != null) vo.setSellerNickname(seller.getNickname());

        return vo;
    }
}
