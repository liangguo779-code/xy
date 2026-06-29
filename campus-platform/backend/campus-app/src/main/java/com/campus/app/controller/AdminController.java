package com.campus.app.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.result.R;
import com.campus.common.entity.User;
import com.campus.common.mapper.UserMapper;
import com.campus.trade.entity.*;
import com.campus.trade.mapper.*;
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

    private final UserMapper userMapper;
    private final GoodsMapper goodsMapper;
    private final OrderMapper orderMapper;
    private final TradePostMapper postMapper;
    private final DisputeMapper disputeMapper;
    private final ReportMapper reportMapper;

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> result = new HashMap<>();

        // 用户统计
        try {
            long userCount = userMapper.selectCount(null);
            long activeCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
            result.put("userCount", userCount);
            result.put("activeCount", activeCount);
        } catch (Exception e) {
            result.put("userError", "用户统计不可用");
        }

        // 商品统计
        try {
            long goodsTotal = goodsMapper.selectCount(null);
            long onSale = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 0));
            long pendingReview = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 3));
            result.put("goodsCount", onSale);
            result.put("goodsTotal", goodsTotal);
            result.put("goodsPendingReview", pendingReview);
        } catch (Exception e) {
            result.put("goodsError", "商品统计不可用");
        }

        // 订单统计
        try {
            long orderTotal = orderMapper.selectCount(null);
            long orderPending = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, 0));
            long orderCompleted = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, 3));
            result.put("orderCount", orderTotal);
            result.put("orderPending", orderPending);
            result.put("orderCompleted", orderCompleted);
        } catch (Exception e) {
            result.put("orderError", "订单统计不可用");
        }

        // 帖子统计
        try {
            long postTotal = postMapper.selectCount(null);
            long postActive = postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1));
            result.put("postCount", postTotal);
            result.put("postActive", postActive);
        } catch (Exception e) {
            result.put("postError", "帖子统计不可用");
        }

        // 纠纷统计
        try {
            long disputeTotal = disputeMapper.selectCount(null);
            long disputePending = disputeMapper.selectCount(new LambdaQueryWrapper<Dispute>().eq(Dispute::getStatus, 0));
            result.put("disputeTotal", disputeTotal);
            result.put("disputePending", disputePending);
        } catch (Exception e) {
            result.put("disputeError", "纠纷统计不可用");
        }

        // 举报统计
        try {
            long reportTotal = reportMapper.selectCount(null);
            long reportPending = reportMapper.selectCount(new LambdaQueryWrapper<Report>().eq(Report::getStatus, 0));
            result.put("reportTotal", reportTotal);
            result.put("reportPending", reportPending);
        } catch (Exception e) {
            result.put("reportError", "举报统计不可用");
        }

        return R.ok(result);
    }
}
