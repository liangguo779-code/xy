package com.campus.trade.schedule;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.trade.entity.DeliveryOrder;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Order;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.DeliveryOrderMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;

    /**
     * 每天凌晨 2 点执行：自动取消超过 3 天未处理的订单
     * 包括：自提待确认(PENDING=0)、配送协商中(DELIVERY_NEGOTIATING=5)、待派单(PENDING_DELIVERY=6)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void autoCancelPendingOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(3);

        // 查询超时的待确认订单（自提 + 配送）
        List<Order> expiredOrders = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .in(Order::getStatus,
                                OrderStatus.PENDING.getCode(),           // 0: 自提待确认
                                OrderStatus.DELIVERY_NEGOTIATING.getCode(), // 5: 配送协商中
                                OrderStatus.PENDING_DELIVERY.getCode())   // 6: 待派单
                        .lt(Order::getCreateTime, deadline)
        );

        if (expiredOrders.isEmpty()) {
            return;
        }

        for (Order order : expiredOrders) {
            order.setStatus(OrderStatus.CANCELLED.getCode());
            orderMapper.updateById(order);

            // 取消关联的配送工单（如果有）
            if (order.getDealType() != null && order.getDealType() == 1) {
                DeliveryOrder deliveryOrder = deliveryOrderMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeliveryOrder>()
                                .eq(DeliveryOrder::getOrderId, order.getId())
                                .eq(DeliveryOrder::getStatus, 0)); // 只取消待接单的
                if (deliveryOrder != null) {
                    deliveryOrder.setStatus(-1);
                    deliveryOrderMapper.updateById(deliveryOrder);
                }
            }

            // 检查是否还有其他进行中的订单
            Long otherActiveOrders = orderMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                            .eq(Order::getGoodsId, order.getGoodsId())
                            .ne(Order::getId, order.getId())
                            .ne(Order::getStatus, OrderStatus.COMPLETED.getCode())
                            .ne(Order::getStatus, OrderStatus.CANCELLED.getCode()));

            // 如果没有其他活跃订单，恢复商品为在售状态
            if (otherActiveOrders == 0) {
                goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                        .eq(Goods::getId, order.getGoodsId())
                        .eq(Goods::getStatus, 3)
                        .set(Goods::getStatus, 0)
                        .set(Goods::getOffReason, null));
            }

            log.info("自动取消超时订单: orderId={}, orderNo={}, goodsId={}, status={}, 创建时间={}",
                    order.getId(), order.getOrderNo(), order.getGoodsId(), order.getStatus(), order.getCreateTime());
        }

        log.info("定时任务: 自动取消 {} 个超时订单", expiredOrders.size());
    }

    /**
     * 每天凌晨 3 点执行：自动下架超过 30 天的在售商品
     * 判断逻辑：优先用 refreshTime（最近刷新时间），没有则用 createTime（发布时间）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void autoDelistExpiredGoods() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(30);

        // 查询所有在售商品，然后在代码中判断是否超期
        List<Goods> onSaleGoods = goodsMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Goods>()
                        .eq(Goods::getStatus, 0)
        );

        int count = 0;
        for (Goods goods : onSaleGoods) {
            // 用 refreshTime 判断，没有则用 createTime
            LocalDateTime lastActive = goods.getRefreshTime() != null
                    ? goods.getRefreshTime() : goods.getCreateTime();
            if (lastActive != null && lastActive.isBefore(deadline)) {
                goods.setStatus(1);
                goods.setOffReason("超过30天未刷新，系统自动下架");
                goodsMapper.updateById(goods);
                count++;
                log.info("自动下架过期商品: goodsId={}, title={}, 最后活跃时间={}",
                        goods.getId(), goods.getTitle(), lastActive);
            }
        }

        if (count > 0) {
            log.info("定时任务: 自动下架 {} 个超过30天的过期商品", count);
        }
    }
}
