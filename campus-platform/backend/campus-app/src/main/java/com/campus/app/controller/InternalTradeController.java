package com.campus.app.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.result.R;
import com.campus.trade.entity.*;
import com.campus.app.dto.DisputeStatsVO;
import com.campus.app.dto.GoodsStatsVO;
import com.campus.app.dto.OrderStatsVO;
import com.campus.app.dto.ReportStatsVO;
import com.campus.trade.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部统计查询接口，当前由 AdminController 直接聚合，保留给未来服务拆分使用。
 * <p>当前无调用方：全库无 Feign，前端与各业务模块均未请求 /internal/**。
 */
@RestController
@RequestMapping("/internal/trade")
@RequiredArgsConstructor
public class InternalTradeController {

    private final GoodsMapper goodsMapper;
    private final OrderMapper orderMapper;
    private final DisputeMapper disputeMapper;
    private final ReportMapper reportMapper;

    /** 获取商品统计数据 */
    @GetMapping("/stats/goods")
    public R<GoodsStatsVO> getGoodsStats() {
        long total = goodsMapper.selectCount(null);
        long onSale = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 0)); // status=0 在售
        long pendingReview = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 3)); // status=3 待审核
        GoodsStatsVO vo = new GoodsStatsVO();
        vo.setTotal(total);
        vo.setOnSale(onSale);
        vo.setPendingReview(pendingReview);
        return R.ok(vo);
    }

    /** 获取订单统计数据 */
    @GetMapping("/stats/orders")
    public R<OrderStatsVO> getOrderStats() {
        long total = orderMapper.selectCount(null);
        long pending = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, 0)); // status=0 待支付/待处理
        long completed = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, 3)); // status=3 已完成
        OrderStatsVO vo = new OrderStatsVO();
        vo.setTotal(total);
        vo.setPending(pending);
        vo.setCompleted(completed);
        return R.ok(vo);
    }

    /** 获取纠纷统计数据 */
    @GetMapping("/stats/disputes")
    public R<DisputeStatsVO> getDisputeStats() {
        long total = disputeMapper.selectCount(null);
        long pending = disputeMapper.selectCount(new LambdaQueryWrapper<Dispute>().eq(Dispute::getStatus, 0));
        DisputeStatsVO vo = new DisputeStatsVO();
        vo.setTotal(total);
        vo.setPending(pending);
        return R.ok(vo);
    }

    /** 获取举报统计数据 */
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
