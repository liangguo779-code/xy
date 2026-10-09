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
import com.campus.trade.enums.DeliveryStatus;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.*;
import com.campus.trade.service.DeliveryService;
import com.campus.trade.service.GoodsIndexService;
import com.campus.trade.service.OrderService;
import com.campus.trade.service.OrderTimeoutService;
import com.campus.trade.service.ReviewService;
import com.campus.trade.service.TradeRequestTimeoutService;
import com.campus.trade.websocket.ChatWebSocketHandler;
import com.campus.common.entity.User;
import com.campus.common.entity.Address;
import com.campus.common.mapper.UserMapper;
import com.campus.common.mapper.AddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** 订单服务实现，驱动自提/配送交易状态机，配合分布式锁与行锁防止超卖/竞态 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final AddressMapper addressMapper;
    private final ReviewMapper reviewMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryService deliveryService;
    private final GoodsIndexService goodsIndexService;
    private final ChatWebSocketHandler wsHandler;
    private final BanService banService;
    private final com.campus.common.service.NotificationService notificationService;
    private final ReviewService reviewService;
    private final RedissonClient redisson;
    private final OrderTimeoutService orderTimeoutService;
    private final TradeRequestTimeoutService tradeRequestTimeoutService;

    /** 平台配送服务费默认值（元），可通过 delivery.service-fee 配置 */
    @Value("${delivery.service-fee:5.00}")
    private BigDecimal defaultServiceFee;

    /**
     * 确认后创建订单：分布式锁+行锁防并发超卖，自提/配送分别进入各自状态分支。
     *
     * <p>库存占位分两条路径：聊天确认场景（chatSessionId 非空）在发起交易时已扣减占位
     * （reserve_ref=会话ID），此处校验归属并把占位转移给订单，不重复扣减；
     * 直接下单场景仍走 0→3 原子扣减。占位转移/扣减失败即回滚订单，建单失败保持原状
     * （占位不释放、请求不作废），修正后可重新确认建单。
     */
    @Override
    @Transactional
    public OrderVO createOrder(Long buyerId, CreateOrderReq req) {
        // 检查买家交易封禁
        BanRecord buyerBan = banService.checkUserBan(buyerId, "trade");
        if (buyerBan != null) {
            throw new BusinessException("您的交易功能已被封禁，原因：" + buyerBan.getReason()
                    + "，解封时间：" + buyerBan.getBanUntil());
        }

        // 下单幂等：同一买家 + 幂等键（缺省退化为商品 ID）在短窗口内只允许提交一次，
        // 防止重复点击/重复请求造成并发下单（参照 DeliveryController 位置上报的 RBucket setIfAbsent 用法）
        String idemKey = "order:idem:" + buyerId + ":"
                + (StringUtils.hasText(req.getIdempotentKey()) ? req.getIdempotentKey() : req.getGoodsId());
        RBucket<String> idemBucket = redisson.getBucket(idemKey);
        if (!idemBucket.setIfAbsent("1", java.time.Duration.ofSeconds(30))) {
            throw new BusinessException("请勿重复提交订单，请稍后再试");
        }
        boolean created = false;

        // Redis 分布式锁：同一商品的下单临界区互斥，防止并发下重复预订/超卖
        RLock lock = redisson.getLock("lock:goods:" + req.getGoodsId());
        try {
            boolean locked = false;
            try {
                locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("系统异常，请稍后重试");
            }
            if (!locked) {
                throw new BusinessException("该商品正在被其他买家下单，请稍后重试");
            }

            // 防重复下单：使用 FOR UPDATE 锁定商品行，获取最新状态
            Goods goods = goodsMapper.selectOne(new LambdaQueryWrapper<Goods>()
                    .eq(Goods::getId, req.getGoodsId()).last("FOR UPDATE"));
            if (goods == null) {
                throw new BusinessException("商品不存在");
            }
            if (goods.getStatus() == 1) {
                throw new BusinessException("商品已下架");
            }
            if (goods.getStatus() == 2) {
                throw new BusinessException("商品已售出");
            }
            // 聊天确认场景：库存已由发起交易时占位，校验占位归属（reserve_ref=会话ID）；
            // 直接下单场景：必须仍在售，已预订（含请求占位）不可抢
            boolean fromChat = req.getChatSessionId() != null;
            if (fromChat) {
                if (goods.getStatus() != 3 || !req.getChatSessionId().equals(goods.getReserveRef())) {
                    throw new BusinessException("交易请求已过期或已失效，请重新发起");
                }
            } else {
                if (goods.getStatus() == 3) {
                    throw new BusinessException("商品已被其他买家预订");
                }
                if (goods.getStatus() != 0) {
                    throw new BusinessException("商品当前不可购买");
                }
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
                // 当前用户必须是买家或卖家之一（聊天中双方都可发起交易）
                boolean isBuyer = buyerId.equals(req.getBuyerId());
                boolean isSeller = buyerId.equals(sellerId);
                if (!isBuyer && !isSeller) {
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
                Address buyerAddress = null;
                if (req.getAddressId() != null) {
                    buyerAddress = addressMapper.selectOne(
                            new LambdaQueryWrapper<Address>()
                                    .eq(Address::getId, req.getAddressId())
                                    .eq(Address::getUserId, actualBuyerId));
                } else {
                    buyerAddress = addressMapper.selectOne(
                            new LambdaQueryWrapper<Address>()
                                    .eq(Address::getUserId, actualBuyerId)
                                    .eq(Address::getIsDefault, 1)
                                    .last("LIMIT 1"));
                }
                if (buyerAddress == null) {
                    throw new BusinessException("买家尚未添加收货地址，请先在收货地址中添加");
                }

                Address sellerAddress = addressMapper.selectOne(
                        new LambdaQueryWrapper<Address>()
                                .eq(Address::getUserId, sellerId)
                                .eq(Address::getIsDefault, 1)
                                .last("LIMIT 1"));
                if (sellerAddress == null) {
                    throw new BusinessException("卖家尚未添加收货地址，无法发起配送");
                }

                order.setStatus(OrderStatus.DELIVERY_NEGOTIATING.getCode());
                order.setServiceFee(defaultServiceFee);
                order.setDeliveryFeePayer(req.getDeliveryFeePayer() != null ? req.getDeliveryFeePayer() : "buyer");
            }

            orderMapper.insert(order);

            // 库存占位落定：聊天确认场景把占位从会话转移给订单（不重复扣减），
            // 直接下单场景条件更新原子扣减（0→3）。二者均为条件更新，失败则回滚订单。
            // status=3 双义说明：既表示管理侧"待审核"，交易流程用作"已预订"锁定位（见 Goods.status 注释）
            int reserved;
            if (fromChat) {
                reserved = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                        .eq(Goods::getId, goods.getId())
                        .eq(Goods::getStatus, 3)
                        .eq(Goods::getReserveRef, req.getChatSessionId())
                        .set(Goods::getReserveRef, order.getId())); // 占位持有者：会话 → 订单
            } else {
                reserved = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                        .eq(Goods::getId, goods.getId())
                        .eq(Goods::getStatus, 0) // 0=在售
                        .set(Goods::getStatus, 3) // 3=待审核/已预订
                        .set(Goods::getReserveRef, order.getId()));
            }
            if (reserved == 0) {
                throw new BusinessException(fromChat
                        ? "交易请求已过期或已失效，请重新发起"
                        : "商品状态已变化，请刷新后重试");
            }

            // 建单成功：请求生命周期结束，撤销其过期延时任务（afterCommit 执行——
            // 回滚时不撤销，请求与安全网保持有效，修正后可重新确认建单）
            Long chatSessionId = req.getChatSessionId();
            if (chatSessionId != null) {
                if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    tradeRequestTimeoutService.cancelExpireTask(chatSessionId);
                                }
                            });
                } else {
                    tradeRequestTimeoutService.cancelExpireTask(chatSessionId);
                }
            }

            // ES 状态同步与通知移出事务：提交后异步执行，ES 故障/通知延迟不再拖垮下单主流程
            Long goodsId = goods.getId();
            Long sellerUserId = goods.getUserId();
            runAfterCommitAsync(() -> {
                try {
                    // 已预订商品保留展示（标记"已预订"）：同步 ES 最新占位状态（reserve_ref 已转移给订单）
                    Goods indexed = goodsMapper.selectById(goodsId);
                    if (indexed != null) {
                        goodsIndexService.indexGoods(indexed);
                    }
                } catch (Exception e) {
                    log.warn("下单后同步 ES 状态失败: goodsId={}", goodsId, e);
                }
                // 通知对方
                notifyUser(sellerUserId, "new_order", order);
            });

            log.info("订单创建: orderNo={}, dealType={}", order.getOrderNo(), req.getDealType());
            created = true;
            return getOrderDetail(buyerId, order.getId());
        } finally {
            if (!created) {
                // 创建失败（校验不通过/商品状态冲突等）释放幂等键，允许用户修正后立即重试
                try {
                    idemBucket.delete();
                } catch (Exception e) {
                    log.warn("释放下单幂等键失败: {}", idemKey, e);
                }
            }
            // 事务提交/回滚后再释放锁，防止并发请求读到未提交的中间状态
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager
                        .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (lock.isHeldByCurrentThread()) {
                                    lock.unlock();
                                }
                            }
                        });
            } else if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 卖家确认自提订单，确认后买家可凭核销码自提 */
    @Override
    @Transactional
    public OrderVO confirmOrder(Long sellerId, Long orderId) {
        Order order = getOrderById(orderId);
        if (!order.getSellerId().equals(sellerId)) {
            throw new BusinessException(403, "无权操作");
        }

        // 条件更新防与超时自动取消竞态：仅当仍为待确认才流转，失败说明状态已被其他线程改变（如已取消）
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.PENDING.getCode())
                .set(Order::getStatus, OrderStatus.CONFIRMED.getCode())
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        // 通知买家：卖家已确认，可凭核销码自提
        notifyUser(order.getBuyerId(), "order_confirmed", Map.of(
                "orderId", order.getId(),
                "verifyCode", order.getVerifyCode(),
                "msg", "卖家已确认交易，请前往约定地点自提。核销码: " + order.getVerifyCode()
        ));

        return getOrderDetail(sellerId, orderId);
    }

    /** 核销自提交易：行锁防取消竞态，校验核销码后订单完成、商品置为已售出 */
    @Override
    @Transactional
    public OrderVO completeSelfPickup(Long sellerId, Long orderId, String verifyCode) {
        // 使用 FOR UPDATE 加行锁，防止与 cancelOrder 竞态
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .last("FOR UPDATE"));
        if (order == null) throw new BusinessException("订单不存在");
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

        // 完成交易（条件更新带前置状态谓词：仅当仍为待核销才流转，更新 0 行说明状态已被并发改变，
        // 防止 updateById 全量覆盖把 CANCELLED 等状态击穿为 COMPLETED，P0-4）
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.CONFIRMED.getCode())
                .set(Order::getStatus, OrderStatus.COMPLETED.getCode())
                .set(Order::getCompleteTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setCompleteTime(LocalDateTime.now());

        // 订单进入终态，移除未到期延时任务
        orderTimeoutService.cancelPendingTasks(order.getId());

        // 商品标记已售出（仅从已预订状态转换，条件更新防竞态）
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, order.getGoodsId())
                .eq(Goods::getStatus, 3)
                .set(Goods::getStatus, 2));

        // 已售出不再展示：移出 ES 检索（与手动标记售出/下架同模式），提交后异步执行
        Long soldGoodsId = order.getGoodsId();
        runAfterCommitAsync(() -> {
            try {
                goodsIndexService.deleteGoods(soldGoodsId);
            } catch (Exception e) {
                log.warn("售出后同步 ES 下架失败: goodsId={}", soldGoodsId, e);
            }
        });

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

    /** 配送服务费支付入口，已废弃（改为线下结算），保留仅为兼容旧调用 */
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

    /** 确认配送安排并自动创建配送工单进入派单大厅 */
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

        // 更新跑腿费付款方并流转状态（条件更新带前置状态谓词：仅当仍为协商中才流转，
        // 防止 updateById 全量覆盖把并发取消的 CANCELLED 击穿为待派单，P0-4）
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.DELIVERY_NEGOTIATING.getCode())
                .set(Order::getDeliveryFeePayer, deliveryFeePayer)
                .set(Order::getStatus, OrderStatus.PENDING_DELIVERY.getCode())
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        order.setDeliveryFeePayer(deliveryFeePayer);
        order.setStatus(OrderStatus.PENDING_DELIVERY.getCode());

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

    /** 买家确认收货：订单完成、商品置已售出并通知骑手与双方评价 */
    @Override
    @Transactional
    public OrderVO confirmReceive(Long buyerId, Long orderId) {
        Order order = getOrderById(orderId);
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(403, "无权操作");
        }

        // 条件更新防与超时自动确认收货竞态：仅当仍为已送达才流转，失败说明状态已被其他线程改变
        LocalDateTime completeTime = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.DELIVERED.getCode())
                .set(Order::getStatus, OrderStatus.COMPLETED.getCode())
                .set(Order::getCompleteTime, completeTime));
        if (updated == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        // 订单进入终态，移除未到期的超时自动确认收货任务
        orderTimeoutService.cancelPendingTasks(order.getId());

        // 商品标记已售出（仅从已预订状态转换，条件更新防竞态）
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, order.getGoodsId())
                .eq(Goods::getStatus, 3)
                .set(Goods::getStatus, 2));

        // 已售出不再展示：移出 ES 检索（与手动标记售出/下架同模式），提交后异步执行
        Long soldGoodsId = order.getGoodsId();
        runAfterCommitAsync(() -> {
            try {
                goodsIndexService.deleteGoods(soldGoodsId);
            } catch (Exception e) {
                log.warn("售出后同步 ES 下架失败: goodsId={}", soldGoodsId, e);
            }
        });

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

    /** 取消订单：已派单后不可取消；无其他进行中订单时恢复商品为在售 */
    @Override
    @Transactional
    public OrderVO cancelOrder(Long userId, Long orderId) {
        // 使用 FOR UPDATE 加行锁，防止与 completeSelfPickup/acceptOrder 竞态
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .last("FOR UPDATE"));
        if (order == null) throw new BusinessException("订单不存在");
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

        // 条件更新带前置状态谓词：仅当仍处于可取消状态才流转，更新 0 行说明状态已被并发改变，
        // 防止 updateById 全量覆盖把并发确认/完成的状态击穿为 CANCELLED（P0-4）
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .in(Order::getStatus,
                        OrderStatus.PENDING.getCode(),
                        OrderStatus.CONFIRMED.getCode(),
                        OrderStatus.DELIVERY_NEGOTIATING.getCode(),
                        OrderStatus.PENDING_DELIVERY.getCode())
                .set(Order::getStatus, OrderStatus.CANCELLED.getCode())
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        order.setStatus(OrderStatus.CANCELLED.getCode());

        // 订单进入终态，移除未到期延时任务
        orderTimeoutService.cancelPendingTasks(order.getId());

        // 如果是配送订单，取消关联的配送工单（条件更新：仅待接单状态的工单可取消）
        if (order.getDealType() != null && order.getDealType() == 1) {
            deliveryOrderMapper.update(null, new LambdaUpdateWrapper<DeliveryOrder>()
                    .eq(DeliveryOrder::getOrderId, orderId)
                    .eq(DeliveryOrder::getStatus, DeliveryStatus.PENDING.getCode())
                    .set(DeliveryOrder::getStatus, DeliveryStatus.CANCELLED.getCode()));
        }

        // 取消订单时减少商品的 wantCount
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, order.getGoodsId())
                .setSql("want_count = GREATEST(want_count - 1, 0)"));

        // 回补商品复用超时服务的加锁善后逻辑：与 createOrder 共用 lock:goods:{goodsId}，
        // 消除"先查活跃订单再回补"与并发下单交错造成的超卖竞态（P0-3）
        orderTimeoutService.restoreGoodsIfIdle(order);

        Long otherUserId = order.getBuyerId().equals(userId)
                ? order.getSellerId() : order.getBuyerId();
        notifyUser(otherUserId, "order_cancelled", Map.of(
                "orderId", order.getId(),
                "msg", "对方已取消订单"
        ));

        return getOrderDetail(userId, orderId);
    }

    /** 评价交易，委托 ReviewService 处理 */
    @Override
    public void createReview(Long reviewerId, CreateReviewReq req) {
        reviewService.createReview(reviewerId, req);
    }

    /** 我的订单列表：按买家/卖家角色过滤，inProgress 优先于 status 过滤 */
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

    /** 订单详情：买卖双方与管理员可见，核销码仅买家可见 */
    @Override
    public OrderVO getOrderDetail(Long userId, Long orderId) {
        Order order = getOrderById(orderId);
        // 只有买家、卖家、管理员可以查看订单详情
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            // 检查是否是管理员
            User user = userMapper.selectById(userId);
            if (user == null || !Integer.valueOf(1).equals(user.getRole())) {
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
        // WebSocket 推送延迟到事务提交后，避免回滚后客户端收到过期通知；
        // 若已在事务外（异步线程/事务后回调）则直接推送
        Runnable push = () -> {
            try {
                wsHandler.pushToUser(userId, Map.of("type", type, "data", data));
            } catch (Exception e) {
                log.warn("WebSocket 推送失败: userId={}, type={}", userId, type, e);
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            push.run();
                        }
                    });
        } else {
            push.run();
        }
    }

    /**
     * 事务提交后异步执行旁路操作（ES 同步、通知等）：
     * 移出事务保证故障不拖垮主流程，异步执行保证不阻塞接口响应；无事务上下文时直接异步执行。
     */
    private void runAfterCommitAsync(Runnable task) {
        Runnable asyncTask = () -> java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("事务提交后异步任务执行失败", e);
            }
        });
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            asyncTask.run();
                        }
                    });
        } else {
            asyncTask.run();
        }
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
            User buyer = userMapper.selectById(order.getBuyerId());
            if (buyer != null) {
                vo.setBuyerNickname(buyer.getNickname());
            }
        } catch (Exception e) {
            log.warn("获取买家信息失败: {}", order.getBuyerId());
        }
        try {
            User seller = userMapper.selectById(order.getSellerId());
            if (seller != null) {
                vo.setSellerNickname(seller.getNickname());
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
