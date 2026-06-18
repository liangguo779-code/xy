package com.campus.trade.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.campus.user.feign.UserFeignClient;
import com.campus.user.feign.dto.AddressVO;
import com.campus.user.feign.dto.UserVO;
import com.campus.common.result.R;
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

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final UserFeignClient userFeignClient;
    private final ReviewMapper reviewMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryService deliveryService;
    private final ChatWebSocketHandler wsHandler;
    private final BanService banService;
    private final com.campus.common.service.NotificationService notificationService;

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

        // 防重复下单：使用 FOR UPDATE 锁定商品行，获取最新状态
        Goods goods = goodsMapper.selectOne(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getId, req.getGoodsId()).last("FOR UPDATE"));
        if (goods == null || goods.getStatus() != 0) {
            throw new BusinessException("商品不存在或已下架");
        }

        Long existingOrder = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getGoodsId, goods.getId())
                        .ne(Order::getStatus, OrderStatus.COMPLETED.getCode())
                        .ne(Order::getStatus, OrderStatus.CANCELLED.getCode()));
        if (existingOrder > 0) {
            throw new BusinessException("该商品已有进行中的订单");
        }

        // 确定买家和卖家：严格校验，防止伪造
        Long sellerId = goods.getUserId();
        Long actualBuyerId;

        if (req.getBuyerId() != null) {
            // 前端指定了买家ID（聊天确认场景）：严格校验
            if (req.getBuyerId().equals(sellerId)) {
                throw new BusinessException("买家不能是卖家本人");
            }
            // 卖家不能指定买家ID（防止卖家冒充任意用户下单）
            if (buyerId.equals(sellerId)) {
                throw new BusinessException("卖家不能代替买家创建订单，请让买家发起交易");
            }
            // 当前用户必须是指定的买家
            if (!buyerId.equals(req.getBuyerId())) {
                throw new BusinessException(403, "无权创建此订单");
            }
            actualBuyerId = req.getBuyerId();
        } else {
            // 未指定买家：当前用户即为买家
            if (buyerId.equals(sellerId)) {
                throw new BusinessException("不能购买自己发布的商品");
            }
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
            // 自提流程：创建订单后等待卖家确认
            order.setStatus(OrderStatus.PENDING.getCode());
            order.setVerifyCode(RandomUtil.randomString(6).toUpperCase());
            order.setPickupLocation(req.getPickupLocation());
            order.setPickupTime(req.getPickupTime() != null
                    ? LocalDateTime.parse(req.getPickupTime()) : null);
        } else {
            // 配送流程：校验双方地址
            AddressVO buyerAddr = null;
            try {
                if (req.getAddressId() != null) {
                    R<AddressVO> addrResult = userFeignClient.getAddress(actualBuyerId, req.getAddressId());
                    if (addrResult.getCode() == 200 && addrResult.getData() != null) buyerAddr = addrResult.getData();
                } else {
                    R<AddressVO> addrResult = userFeignClient.getDefaultAddress(actualBuyerId);
                    if (addrResult.getCode() == 200 && addrResult.getData() != null) buyerAddr = addrResult.getData();
                }
            } catch (Exception e) {
                throw new BusinessException("查询买家地址失败，请稍后重试");
            }
            if (buyerAddr == null) {
                throw new BusinessException("买家尚未添加收货地址，请先在收货地址中添加");
            }

            try {
                R<AddressVO> sellerAddrResult = userFeignClient.getDefaultAddress(sellerId);
                if (sellerAddrResult.getCode() != 200 || sellerAddrResult.getData() == null) {
                    throw new BusinessException("卖家尚未添加收货地址，无法发起配送");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException("查询卖家地址失败，请稍后重试");
            }

            order.setStatus(OrderStatus.DELIVERY_NEGOTIATING.getCode());
            order.setServiceFee(defaultServiceFee);
            order.setDeliveryFeePayer(req.getDeliveryFeePayer() != null ? req.getDeliveryFeePayer() : "buyer");
            order.setFloor(req.getFloor() != null ? req.getFloor() : 1);
            order.setHasElevator(req.getHasElevator() != null ? (req.getHasElevator() ? 1 : 0) : 1);
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
        // 校验 deliveryFeePayer 合法性
        if (!"buyer".equals(deliveryFeePayer) && !"seller".equals(deliveryFeePayer)) {
            throw new BusinessException("无效的跑腿费付款方，只能为 buyer 或 seller");
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
        // 通知骑手交易已完成
        DeliveryOrder deliveryOrder = deliveryOrderMapper.selectOne(
                new LambdaQueryWrapper<DeliveryOrder>().eq(DeliveryOrder::getOrderId, orderId));
        if (deliveryOrder != null && deliveryOrder.getRunnerId() != null) {
            notifyUser(deliveryOrder.getRunnerId(), "order_completed", Map.of(
                    "orderId", order.getId(), "msg", "订单已完成，买家已确认收货"
            ));
        }

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
        // 已派单后不能取消（骑手已接单），需联系客服
        if (order.getStatus() >= OrderStatus.ASSIGNED.getCode()) {
            throw new BusinessException("骑手已接单，无法取消，请联系客服");
        }

        order.setStatus(OrderStatus.CANCELLED.getCode());
        orderMapper.updateById(order);

        // 如果是配送订单，取消关联的配送工单
        if (order.getDealType() != null && order.getDealType() == 1) {
            DeliveryOrder deliveryOrder = deliveryOrderMapper.selectOne(
                    new LambdaQueryWrapper<DeliveryOrder>().eq(DeliveryOrder::getOrderId, orderId));
            if (deliveryOrder != null && deliveryOrder.getStatus() == 0) {
                // 只有待接单状态的配送工单可以取消
                deliveryOrder.setStatus(-1); // -1 表示已取消
                deliveryOrderMapper.updateById(deliveryOrder);
            }
        }

        // 取消订单时减少商品的 wantCount
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, order.getGoodsId())
                .setSql("want_count = GREATEST(want_count - 1, 0)"));

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
        review.setTags(req.getTags());
        reviewMapper.insert(review);
    }

    @Override
    public Page<OrderVO> getMyOrders(Long userId, Integer status, String role, Boolean inProgress, int page, int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if ("seller".equals(role)) {
            wrapper.eq(Order::getSellerId, userId);
        } else {
            wrapper.eq(Order::getBuyerId, userId);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        if (Boolean.TRUE.equals(inProgress)) {
            wrapper.ne(Order::getStatus, OrderStatus.COMPLETED.getCode())
                    .ne(Order::getStatus, OrderStatus.CANCELLED.getCode());
        } else if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        Page<Order> orderPage = orderMapper.selectPage(new Page<>(page, size), wrapper);
        Page<OrderVO> result = new Page<>(page, size, orderPage.getTotal());
        result.setRecords(orderPage.getRecords().stream()
                .map(o -> toVO(o, userId)).collect(Collectors.toList()));
        return result;
    }

    @Override
    public OrderVO getOrderDetail(Long userId, Long orderId) {
        Order order = getOrderById(orderId);
        // 只有买家、卖家、管理员可以查看订单详情
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            // 检查是否是管理员
            try {
                R<UserVO> userResult = userFeignClient.getUserById(userId);
                if (userResult.getCode() != 200 || userResult.getData() == null || userResult.getData().getRole() != 1) {
                    throw new BusinessException(403, "无权查看此订单");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
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
        // 时间戳(13位) + 8位随机字符，碰撞概率极低
        return "ORD" + System.currentTimeMillis() + RandomUtil.randomString(8).toUpperCase();
    }

    private void notifyUser(Long userId, String type, Object data) {
        // 持久化通知（事务内，确保一致性）
        try {
            String title = type.replace("_", " ");
            String content = "";
            String extra = null;
            if (data instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) data;
                Object msg = map.get("msg");
                if (msg != null) content = msg.toString();
                extra = OBJECT_MAPPER.writeValueAsString(data);
            } else if (data instanceof Order) {
                Order o = (Order) data;
                title = "新订单";
                content = "订单号: " + o.getOrderNo();
                extra = "{\"orderId\":" + o.getId() + "}";
            }
            notificationService.send(userId, type, title, content, extra);
        } catch (Exception e) {
            log.warn("持久化通知失败: userId={}, type={}", userId, type, e);
        }
        // WebSocket 推送延迟到事务提交后，避免回滚后客户端收到过期通知
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            wsHandler.pushToUser(userId, Map.of("type", type, "data", data));
                        } catch (Exception e) {
                            log.warn("WebSocket 推送失败: userId={}, type={}", userId, type, e);
                        }
                    }
                });
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
        OrderStatus orderStatus = OrderStatus.fromCode(order.getStatus());
        vo.setStatusDesc(orderStatus != null ? orderStatus.getDesc() : "未知状态");
        vo.setPickupLocation(order.getPickupLocation());
        vo.setPickupTime(order.getPickupTime());
        vo.setCompleteTime(order.getCompleteTime());
        vo.setCreateTime(order.getCreateTime());

        // 填充商品和用户信息
        Goods goods = goodsMapper.selectById(order.getGoodsId());
        if (goods != null) {
            vo.setGoodsTitle(goods.getTitle());
            try {
                List<String> images = OBJECT_MAPPER
                        .readValue(goods.getImages(), List.class);
                vo.setGoodsImage(images.isEmpty() ? null : images.get(0));
            } catch (Exception e) {
                vo.setGoodsImage(null);
            }
        }
        try {
            R<UserVO> buyerResult = userFeignClient.getUserById(order.getBuyerId());
            if (buyerResult.getCode() == 200 && buyerResult.getData() != null) {
                vo.setBuyerNickname(buyerResult.getData().getNickname());
            }
        } catch (Exception e) {
            log.warn("获取买家信息失败: {}", order.getBuyerId());
        }
        try {
            R<UserVO> sellerResult = userFeignClient.getUserById(order.getSellerId());
            if (sellerResult.getCode() == 200 && sellerResult.getData() != null) {
                vo.setSellerNickname(sellerResult.getData().getNickname());
            }
        } catch (Exception e) {
            log.warn("获取卖家信息失败: {}", order.getSellerId());
        }

        // 填充配送工单ID
        if (order.getDealType() != null && order.getDealType() == 1) {
            DeliveryOrder deliveryOrder = deliveryOrderMapper.selectOne(
                    new LambdaQueryWrapper<DeliveryOrder>().eq(DeliveryOrder::getOrderId, order.getId()));
            if (deliveryOrder != null) vo.setDeliveryOrderId(deliveryOrder.getId());
        }

        return vo;
    }
}
