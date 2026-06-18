package com.campus.trade.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.result.R;
import com.campus.trade.entity.*;
import com.campus.trade.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部调用接口（供 admin 服务通过 Feign 调用统计数据）
 */
@RestController
@RequestMapping("/internal/trade")
@RequiredArgsConstructor
public class InternalTradeController {

    private final GoodsMapper goodsMapper;
    private final OrderMapper orderMapper;
    private final DisputeMapper disputeMapper;
    private final ReportMapper reportMapper;

    @GetMapping("/stats/goods")
    public R<Map<String, Object>> getGoodsStats() {
        long total = goodsMapper.selectCount(null);
        long onSale = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 0));
        long pendingReview = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 3));
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("onSale", onSale);
        stats.put("pendingReview", pendingReview);
        return R.ok(stats);
    }

    @GetMapping("/stats/orders")
    public R<Map<String, Object>> getOrderStats() {
        long total = orderMapper.selectCount(null);
        long pending = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, 0));
        long completed = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, 3));
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("completed", completed);
        return R.ok(stats);
    }

    @GetMapping("/stats/disputes")
    public R<Map<String, Object>> getDisputeStats() {
        long total = disputeMapper.selectCount(null);
        long pending = disputeMapper.selectCount(new LambdaQueryWrapper<Dispute>().eq(Dispute::getStatus, 0));
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        return R.ok(stats);
    }

    @GetMapping("/stats/reports")
    public R<Map<String, Object>> getReportStats() {
        long total = reportMapper.selectCount(null);
        long pending = reportMapper.selectCount(new LambdaQueryWrapper<Report>().eq(Report::getStatus, 0));
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        return R.ok(stats);
    }
}
