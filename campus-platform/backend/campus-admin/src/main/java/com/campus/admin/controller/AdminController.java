package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.campus.common.result.R;
import com.campus.feign.forum.ForumFeignClient;
import com.campus.feign.trade.TradeFeignClient;
import com.campus.feign.user.UserFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import com.campus.feign.user.dto.UserStatsVO;
import com.campus.feign.trade.dto.GoodsStatsVO;
import com.campus.feign.trade.dto.OrderStatsVO;
import com.campus.feign.forum.dto.PostStatsVO;

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
            R<UserStatsVO> userStats = userFeignClient.getUserStats();
            if (userStats.getCode() == 200 && userStats.getData() != null) {
                result.put("userCount", userStats.getData().getUserCount());
                result.put("activeCount", userStats.getData().getActiveCount());
            }
        } catch (Exception e) {
            result.put("userError", "用户服务不可用");
        }

        // 获取商品统计
        try {
            R<GoodsStatsVO> goodsStats = tradeFeignClient.getGoodsStats();
            if (goodsStats.getCode() == 200 && goodsStats.getData() != null) {
                result.put("goodsCount", goodsStats.getData().getOnSale());
            }
        } catch (Exception e) {
            result.put("goodsError", "交易服务不可用");
        }

        // 获取订单统计
        try {
            R<OrderStatsVO> orderStats = tradeFeignClient.getOrderStats();
            if (orderStats.getCode() == 200 && orderStats.getData() != null) {
                result.put("orderCount", orderStats.getData().getTotal());
            }
        } catch (Exception e) {
            result.put("orderError", "交易服务不可用");
        }

        // 获取帖子统计
        try {
            R<PostStatsVO> postStats = forumFeignClient.getPostStats();
            if (postStats.getCode() == 200 && postStats.getData() != null) {
                result.put("postCount", postStats.getData().getTotal());
            }
        } catch (Exception e) {
            result.put("postError", "论坛服务不可用");
        }

        return R.ok(result);
    }
}
