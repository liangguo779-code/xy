package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.campus.common.result.R;
import com.campus.forum.feign.ForumFeignClient;
import com.campus.trade.feign.TradeFeignClient;
import com.campus.user.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminController {

    private final UserFeignClient userFeignClient;
    private final TradeFeignClient tradeFeignClient;
    private final ForumFeignClient forumFeignClient;

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> result = new HashMap<>();

        // 获取用户统计
        try {
            R<Map<String, Object>> userStats = userFeignClient.getUserStats();
            if (userStats.getCode() == 200 && userStats.getData() != null) {
                result.putAll(userStats.getData());
            }
        } catch (Exception e) {
            result.put("userError", "用户服务不可用");
        }

        // 获取商品统计
        try {
            R<Map<String, Object>> goodsStats = tradeFeignClient.getGoodsStats();
            if (goodsStats.getCode() == 200 && goodsStats.getData() != null) {
                result.put("goodsCount", goodsStats.getData().get("onSale"));
            }
        } catch (Exception e) {
            result.put("goodsError", "交易服务不可用");
        }

        // 获取订单统计
        try {
            R<Map<String, Object>> orderStats = tradeFeignClient.getOrderStats();
            if (orderStats.getCode() == 200 && orderStats.getData() != null) {
                result.put("orderCount", orderStats.getData().get("total"));
            }
        } catch (Exception e) {
            result.put("orderError", "交易服务不可用");
        }

        // 获取帖子统计
        try {
            R<Map<String, Object>> postStats = forumFeignClient.getPostStats();
            if (postStats.getCode() == 200 && postStats.getData() != null) {
                result.put("postCount", postStats.getData().get("total"));
            }
        } catch (Exception e) {
            result.put("postError", "论坛服务不可用");
        }

        return R.ok(result);
    }
}
