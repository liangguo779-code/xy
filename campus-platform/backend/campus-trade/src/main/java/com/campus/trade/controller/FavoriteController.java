package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.trade.entity.Goods;
import com.campus.trade.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 商品收藏接口 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** 收藏商品 */
    @PostMapping("/{goodsId}")
    public R<Void> add(@PathVariable Long goodsId) {
        favoriteService.addFavorite(StpUtil.getLoginIdAsLong(), goodsId);
        return R.ok();
    }

    /** 取消收藏商品 */
    @DeleteMapping("/{goodsId}")
    public R<Void> remove(@PathVariable Long goodsId) {
        favoriteService.removeFavorite(StpUtil.getLoginIdAsLong(), goodsId);
        return R.ok();
    }

    /** 获取我的收藏商品列表 */
    @GetMapping
    public R<List<Goods>> list() {
        return R.ok(favoriteService.getMyFavorites(StpUtil.getLoginIdAsLong()));
    }

    /** 查询我是否已收藏该商品 */
    @GetMapping("/check/{goodsId}")
    public R<Boolean> check(@PathVariable Long goodsId) {
        return R.ok(favoriteService.isFavorited(StpUtil.getLoginIdAsLong(), goodsId));
    }
}
