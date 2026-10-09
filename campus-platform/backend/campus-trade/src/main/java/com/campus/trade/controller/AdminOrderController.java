package com.campus.trade.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Order;
import com.campus.trade.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理端订单接口：订单查询与总量统计（需 ADMIN 角色） */
@RestController
@RequestMapping("/api/admin/orders")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderMapper orderMapper;

    /** 分页查询订单，可按状态、交易方式过滤，按创建时间倒序 */
    @GetMapping
    public R<Page<Order>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer dealType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Order::getStatus, status);
        if (dealType != null) wrapper.eq(Order::getDealType, dealType);
        wrapper.orderByDesc(Order::getCreateTime);
        return R.ok(orderMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /** 订单总览统计：总数/已完成/已取消 */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        long total = orderMapper.selectCount(null);
        long completed = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, 3));   // 3-已完成
        long cancelled = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, 4));   // 4-已取消
        return R.ok(Map.of("total", total, "completed", completed, "cancelled", cancelled));
    }
}
