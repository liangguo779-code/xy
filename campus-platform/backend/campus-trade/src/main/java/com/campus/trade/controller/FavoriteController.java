package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.trade.entity.Goods;
import com.campus.trade.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/{goodsId}")
    public R<Void> add(@PathVariable Long goodsId) {
        favoriteService.addFavorite(StpUtil.getLoginIdAsLong(), goodsId);
        return R.ok();
    }

    @DeleteMapping("/{goodsId}")
    public R<Void> remove(@PathVariable Long goodsId) {
        favoriteService.removeFavorite(StpUtil.getLoginIdAsLong(), goodsId);
        return R.ok();
    }

    @GetMapping
    public R<List<Goods>> list() {
        return R.ok(favoriteService.getMyFavorites(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/check/{goodsId}")
    public R<Boolean> check(@PathVariable Long goodsId) {
        return R.ok(favoriteService.isFavorited(StpUtil.getLoginIdAsLong(), goodsId));
    }
}
