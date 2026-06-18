package com.campus.trade.feign;

import com.campus.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "campus-trade-service", path = "/internal/trade")
public interface TradeFeignClient {

    @GetMapping("/stats/goods")
    R<Map<String, Object>> getGoodsStats();

    @GetMapping("/stats/orders")
    R<Map<String, Object>> getOrderStats();

    @GetMapping("/stats/disputes")
    R<Map<String, Object>> getDisputeStats();

    @GetMapping("/stats/reports")
    R<Map<String, Object>> getReportStats();
}
