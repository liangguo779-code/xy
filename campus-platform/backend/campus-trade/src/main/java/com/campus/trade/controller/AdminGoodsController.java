package com.campus.trade.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.R;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.service.GoodsIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/goods")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminGoodsController {

    private final GoodsMapper goodsMapper;
    private final GoodsIndexService goodsIndexService;
    private final com.campus.trade.mapper.OrderMapper orderMapper;

    @GetMapping
    public R<Page<Goods>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Goods::getStatus, status);
        if (keyword != null) {
            wrapper.and(w -> w.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword));
        }
        wrapper.orderByDesc(Goods::getCreateTime);
        return R.ok(goodsMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) throw new BusinessException("商品不存在");
        if (goods.getStatus() != 3) {
            throw new BusinessException("只有待审核的商品才能审核通过");
        }

        // 检查是否有进行中的订单（status=3 可能是"已预订"而非"待审核"）
        Long activeOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<com.campus.trade.entity.Order>()
                        .eq(com.campus.trade.entity.Order::getGoodsId, id)
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.COMPLETED.getCode())
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.CANCELLED.getCode()));
        if (activeOrders > 0) {
            throw new BusinessException("该商品有进行中的订单，无法审核通过");
        }

        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .set(Goods::getStatus, 0)
                .set(Goods::getOffReason, null));
        goods.setStatus(0);
        goodsIndexService.indexGoods(goods);
        return R.ok();
    }

    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) throw new BusinessException("商品不存在");
        if (goods.getStatus() == 2) {
            throw new BusinessException("已售出的商品不能拒绝");
        }

        // 检查是否有进行中的订单
        Long activeOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<com.campus.trade.entity.Order>()
                        .eq(com.campus.trade.entity.Order::getGoodsId, id)
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.COMPLETED.getCode())
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.CANCELLED.getCode()));
        if (activeOrders > 0) {
            throw new BusinessException("该商品有 " + activeOrders + " 个进行中的订单，请先处理订单后再操作");
        }

        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .set(Goods::getStatus, 1)
                .set(Goods::getOffReason, "管理员拒绝：" + (reason != null ? reason : "违规内容")));
        goodsIndexService.deleteGoods(id);
        return R.ok();
    }

    @PutMapping("/{id}/force-off")
    public R<Void> forceOff(@PathVariable Long id, @RequestParam(required = false) String reason) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) throw new BusinessException("商品不存在");
        if (goods.getStatus() != 0 && goods.getStatus() != 3) {
            throw new BusinessException("只有在售或已预订的商品才能强制下架");
        }

        // 检查是否有进行中的订单
        Long activeOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<com.campus.trade.entity.Order>()
                        .eq(com.campus.trade.entity.Order::getGoodsId, id)
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.COMPLETED.getCode())
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.CANCELLED.getCode()));
        if (activeOrders > 0) {
            throw new BusinessException("该商品有 " + activeOrders + " 个进行中的订单，请先处理订单后再操作");
        }

        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .set(Goods::getStatus, 1)
                .set(Goods::getOffReason, "管理员强制下架：" + (reason != null ? reason : "")));
        goodsIndexService.deleteGoods(id);
        return R.ok();
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        long total = goodsMapper.selectCount(null);
        long onSale = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 0));
        long reserved = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 3));
        long sold = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 2));
        return R.ok(Map.of("total", total, "onSale", onSale, "reserved", reserved, "sold", sold));
    }

    @PutMapping("/reindex")
    public R<Void> reindex() {
        goodsIndexService.reindexAll();
        return R.ok();
    }
}
