package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Favorite;
import com.campus.trade.entity.Goods;

import java.util.List;

public interface FavoriteService extends IService<Favorite> {
    void addFavorite(Long userId, Long goodsId);
    void removeFavorite(Long userId, Long goodsId);
    boolean isFavorited(Long userId, Long goodsId);
    List<Goods> getMyFavorites(Long userId);
}
