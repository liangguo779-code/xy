package com.campus.trade.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.result.R;
import com.campus.trade.entity.*;
import com.campus.feign.trade.dto.DisputeStatsVO;
import com.campus.feign.trade.dto.GoodsStatsVO;
import com.campus.feign.trade.dto.OrderStatsVO;
import com.campus.feign.trade.dto.ReportStatsVO;
import com.campus.trade.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public R<GoodsStatsVO> getGoodsStats() {
        long total = goodsMapper.selectCount(null);
        long onSale = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 0));
        long pendingReview = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 3));
        GoodsStatsVO vo = new GoodsStatsVO();
        vo.setTotal(total);
        vo.setOnSale(onSale);
        vo.setPendingReview(pendingReview);
        return R.ok(vo);
    }

    @GetMapping("/stats/orders")
    public R<OrderStatsVO> getOrderStats() {
        long total = orderMapper.selectCount(null);
        long pending = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, 0));
        long completed = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, 3));
        OrderStatsVO vo = new OrderStatsVO();
        vo.setTotal(total);
        vo.setPending(pending);
        vo.setCompleted(completed);
        return R.ok(vo);
    }

    @GetMapping("/stats/disputes")
    public R<DisputeStatsVO> getDisputeStats() {
        long total = disputeMapper.selectCount(null);
        long pending = disputeMapper.selectCount(new LambdaQueryWrapper<Dispute>().eq(Dispute::getStatus, 0));
        DisputeStatsVO vo = new DisputeStatsVO();
        vo.setTotal(total);
        vo.setPending(pending);
        return R.ok(vo);
    }

    @GetMapping("/stats/reports")
    public R<ReportStatsVO> getReportStats() {
        long total = reportMapper.selectCount(null);
        long pending = reportMapper.selectCount(new LambdaQueryWrapper<Report>().eq(Report::getStatus, 0));
        ReportStatsVO vo = new ReportStatsVO();
        vo.setTotal(total);
        vo.setPending(pending);
        return R.ok(vo);
    }
}
