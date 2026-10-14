package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.dto.CreateHotOrderReq;
import com.campus.trade.dto.CreateOrderReq;
import com.campus.trade.dto.CreateReviewReq;
import com.campus.trade.dto.HotTicketVO;
import com.campus.trade.dto.OrderVO;
import com.campus.trade.service.HotSaleOrderService;
import com.campus.trade.service.HotTicketService;
import com.campus.trade.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 订单接口：下单与状态流转（确认/核销/收货/取消）、评价与订单查询、热卖票据 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final HotSaleOrderService hotSaleOrderService;
    private final HotTicketService hotTicketService;

    /** 创建订单（自提或配送，见 CreateOrderReq.dealType） */
    @PostMapping
    public R<OrderVO> createOrder(@Valid @RequestBody CreateOrderReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(orderService.createOrder(userId, req));
    }

    /** 热卖下单：Lua 扣库存 + 票据消息入待确认队列（此刻无 DB 订单行，消费才落库） */
    @PostMapping("/hot")
    public R<HotTicketVO> createHotOrder(@Valid @RequestBody CreateHotOrderReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(hotSaleOrderService.placeOrder(userId, req));
    }

    /** 热卖票据二次确认（购买承诺）：消息迁入已确认队列，返回「处理中」，worker 随后落库进派单大厅 */
    @PutMapping("/hot/{orderNo}/confirm-purchase")
    public R<HotTicketVO> confirmHotPurchase(
            @PathVariable String orderNo,
            @RequestParam(required = false) Long goodsId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(hotSaleOrderService.confirmTicket(userId, orderNo, goodsId));
    }

    /** 热卖票据待确认期取消：摘消息释放库存；确认后不可自助取消，走人工/纠纷 */
    @PutMapping("/hot/{orderNo}/cancel-hot")
    public R<HotTicketVO> cancelHotTicket(
            @PathVariable String orderNo,
            @RequestParam(required = false) Long goodsId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(hotSaleOrderService.cancelTicket(userId, orderNo, goodsId));
    }

    /** 热卖票据详情（含处理中轮询）：消息在则返回阶段；已落库则回填 orderId 供跳转 */
    @GetMapping("/hot/ticket/{orderNo}")
    public R<HotTicketVO> getHotTicket(@PathVariable String orderNo) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(hotTicketService.getTicket(orderNo, userId));
    }

    /** 我的热卖票据（「我的订单」合并展示用，票据排前） */
    @GetMapping("/hot/tickets/my")
    public R<List<HotTicketVO>> myHotTickets() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(hotTicketService.myTickets(userId));
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
