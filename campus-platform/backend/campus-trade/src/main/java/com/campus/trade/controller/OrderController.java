package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.dto.CreateOrderReq;
import com.campus.trade.dto.CreateReviewReq;
import com.campus.trade.dto.OrderVO;
import com.campus.trade.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 订单接口：下单与状态流转（确认/核销/收货/取消）、评价与订单查询 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建订单（自提或配送，见 CreateOrderReq.dealType） */
    @PostMapping
    public R<OrderVO> createOrder(@Valid @RequestBody CreateOrderReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.createOrder(userId, req));
    }

    /** 卖家确认订单 */
    @PutMapping("/{id}/confirm")
    public R<OrderVO> confirmOrder(@PathVariable Long id) {
        Long sellerId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.confirmOrder(sellerId, id));
    }

    /** 自提订单核销完成：卖家校验核销码后完结订单 */
    @PutMapping("/{id}/complete")
    public R<OrderVO> completeOrder(
            @PathVariable Long id,
            @RequestParam String verifyCode) {
        Long sellerId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.completeSelfPickup(sellerId, id, verifyCode));
    }

    /** 买家支付跑腿费 */
    @PutMapping("/{id}/pay-fee")
    public R<OrderVO> payDeliveryFee(@PathVariable Long id) {
        Long buyerId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.payDeliveryFee(buyerId, id));
    }

    /** 买家确认收货，订单完结 */
    @PutMapping("/{id}/confirm-receive")
    public R<OrderVO> confirmReceive(@PathVariable Long id) {
        Long buyerId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.confirmReceive(buyerId, id));
    }

    /** 双方确认配送方案与跑腿费付款方，进入待派单 */
    @PutMapping("/{id}/confirm-delivery")
    public R<OrderVO> confirmDelivery(
            @PathVariable Long id,
            @RequestParam String deliveryFeePayer) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.confirmDelivery(userId, id, deliveryFeePayer));
    }

    /** 取消订单（买卖双方在允许的阶段均可发起） */
    @PutMapping("/{id}/cancel")
    public R<OrderVO> cancelOrder(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.cancelOrder(userId, id));
    }

    /** 对已完成的订单发布评价 */
    @PostMapping("/review")
    public R<Void> createReview(@Valid @RequestBody CreateReviewReq req) {
        Long reviewerId = StpUtil.getLoginIdAsLong();
        orderService.createReview(reviewerId, req);
        return R.ok();
    }

    /** 我的订单列表（role 区分买家/卖家视角，inProgress 只看进行中） */
    @GetMapping("/my")
    public R<Page<OrderVO>> myOrders(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "buyer") String role,
            @RequestParam(required = false) Boolean inProgress,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.getMyOrders(userId, status, role, inProgress, page, size));
    }

    /** 订单详情（仅订单买卖双方可见） */
    @GetMapping("/{id}")
    public R<OrderVO> orderDetail(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.getOrderDetail(userId, id));
    }
}
