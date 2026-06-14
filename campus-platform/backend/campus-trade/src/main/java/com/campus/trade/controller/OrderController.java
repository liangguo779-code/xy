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

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public R<OrderVO> createOrder(@Valid @RequestBody CreateOrderReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.createOrder(userId, req));
    }

    @PutMapping("/{id}/confirm")
    public R<OrderVO> confirmOrder(@PathVariable Long id) {
        Long sellerId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.confirmOrder(sellerId, id));
    }

    @PutMapping("/{id}/complete")
    public R<OrderVO> completeOrder(
            @PathVariable Long id,
            @RequestParam String verifyCode) {
        Long sellerId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.completeSelfPickup(sellerId, id, verifyCode));
    }

    @PutMapping("/{id}/pay-fee")
    public R<OrderVO> payDeliveryFee(@PathVariable Long id) {
        Long buyerId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.payDeliveryFee(buyerId, id));
    }

    @PutMapping("/{id}/confirm-receive")
    public R<OrderVO> confirmReceive(@PathVariable Long id) {
        Long buyerId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.confirmReceive(buyerId, id));
    }

    @PutMapping("/{id}/confirm-delivery")
    public R<OrderVO> confirmDelivery(
            @PathVariable Long id,
            @RequestParam String deliveryFeePayer) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.confirmDelivery(userId, id, deliveryFeePayer));
    }

    @PutMapping("/{id}/cancel")
    public R<OrderVO> cancelOrder(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.cancelOrder(userId, id));
    }

    @PostMapping("/review")
    public R<Void> createReview(@Valid @RequestBody CreateReviewReq req) {
        Long reviewerId = StpUtil.getLoginIdAsLong();
        orderService.createReview(reviewerId, req);
        return R.ok();
    }

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

    @GetMapping("/{id}")
    public R<OrderVO> orderDetail(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.getOrderDetail(userId, id));
    }
}
