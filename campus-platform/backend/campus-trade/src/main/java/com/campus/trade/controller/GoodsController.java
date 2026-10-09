package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.entity.Goods;
import com.campus.trade.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** 商品接口：浏览/搜索/发布/管理自己的商品、擦亮与浏览历史 */
@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    /**
     * 商品列表（多维度筛选+排序）
     */
    @GetMapping
    public R<Page<Goods>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(goodsService.listGoods(keyword, categoryId, userId, type,
                minPrice, maxPrice, condition, sortBy, page, size));
    }

    /**
     * 我的商品列表
     */
    @GetMapping("/my")
    public R<Page<Goods>> myGoods(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(goodsService.listMyGoods(userId, status, page, size));
    }

    /**
     * 推荐商品（个性化）
     */
    @GetMapping("/recommend")
    public R<Page<Goods>> recommend(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            return R.ok(goodsService.recommendGoods(userId, page, size));
        } catch (Exception e) {
            // 未登录或取用户失败时退化为通用推荐
            return R.ok(goodsService.recommendGoods(page, size));
        }
    }

    /** 商品详情（游客也可访问，登录时返回个性化信息） */
    @GetMapping("/{id}")
    public R<Goods> detail(@PathVariable Long id) {
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return R.ok(goodsService.getGoodsDetail(id, userId));
    }

    /** 发布商品 */
    @PostMapping
    public R<Goods> create(@RequestBody Goods goods) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(goodsService.createGoods(userId, goods));
    }

    /** 编辑自己的商品 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Goods goods) {
        Long userId = StpUtil.getLoginIdAsLong();
        goodsService.updateGoods(userId, id, goods);
        return R.ok();
    }

    /** 下架/删除自己的商品，可附下架原因 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @RequestParam(required = false) String reason) {
        Long userId = StpUtil.getLoginIdAsLong();
        goodsService.deleteGoods(userId, id, reason);
        return R.ok();
    }

    /**
     * 标记已售出
     */
    @PutMapping("/{id}/sold")
    public R<Void> markAsSold(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        goodsService.markAsSold(userId, id);
        return R.ok();
    }

    /**
     * 擦亮商品（刷新排名，每天限1次）
     */
    @PutMapping("/{id}/refresh")
    public R<Void> refreshGoods(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        goodsService.refreshGoods(userId, id);
        return R.ok();
    }

    /**
     * 重新上架（已下架的商品）
     */
    @PutMapping("/{id}/reshelf")
    public R<Void> reshelfGoods(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        goodsService.reshelfGoods(userId, id);
        return R.ok();
    }

    /**
     * 浏览历史
     */
    @GetMapping("/browse-history")
    public R<Page<Goods>> browseHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(goodsService.getBrowseHistory(userId, page, size));
    }
}
