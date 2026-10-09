package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Favorite;
import com.campus.trade.entity.Goods;

import java.util.List;

/** 商品收藏服务，管理用户的商品收藏关系 */
public interface FavoriteService extends IService<Favorite> {
    /** 收藏商品 */
    void addFavorite(Long userId, Long goodsId);
    /** 取消收藏 */
    void removeFavorite(Long userId, Long goodsId);
    /** 判断商品是否已被该用户收藏 */
    boolean isFavorited(Long userId, Long goodsId);
    /** 查询我的收藏商品列表 */
    List<Goods> getMyFavorites(Long userId);
}
