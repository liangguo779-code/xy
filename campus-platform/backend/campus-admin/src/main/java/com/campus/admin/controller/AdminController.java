package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.result.R;
import com.campus.forum.mapper.PostMapper;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Order;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.OrderMapper;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminController {

    private final UserMapper userMapper;
    private final GoodsMapper goodsMapper;
    private final OrderMapper orderMapper;
    private final PostMapper postMapper;

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        long userCount = userMapper.selectCount(null);
        long goodsCount = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 0));
        long orderCount = orderMapper.selectCount(null);
        long postCount = postMapper.selectCount(null);

        return R.ok(Map.of(
                "userCount", userCount,
                "goodsCount", goodsCount,
                "orderCount", orderCount,
                "postCount", postCount
        ));
    }
}
