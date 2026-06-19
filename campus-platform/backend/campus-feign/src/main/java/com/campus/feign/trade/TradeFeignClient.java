package com.campus.feign.trade;

import com.campus.common.result.R;
import com.campus.feign.trade.dto.DisputeStatsVO;
import com.campus.feign.trade.dto.GoodsStatsVO;
import com.campus.feign.trade.dto.OrderStatsVO;
import com.campus.feign.trade.dto.ReportStatsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "campus-trade-service", path = "/internal/trade")
public interface TradeFeignClient {

    @GetMapping("/stats/goods")
    R<GoodsStatsVO> getGoodsStats();

    @GetMapping("/stats/orders")
    R<OrderStatsVO> getOrderStats();

    @GetMapping("/stats/disputes")
    R<DisputeStatsVO> getDisputeStats();

    @GetMapping("/stats/reports")
    R<ReportStatsVO> getReportStats();
}
