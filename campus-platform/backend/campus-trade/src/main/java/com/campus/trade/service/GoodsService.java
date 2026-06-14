package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Goods;

import java.math.BigDecimal;

public interface GoodsService extends IService<Goods> {

    /**
     * 商品列表（多维度筛选+排序）
     */
    Page<Goods> listGoods(String keyword, Long categoryId, Long userId, Integer type,
                          BigDecimal minPrice, BigDecimal maxPrice,
                          String condition, String sortBy,
                          int page, int size);

    /**
     * 推荐商品（热门+最新）
     */
    Page<Goods> recommendGoods(int page, int size);

    /**
     * 个性化推荐（基于用户收藏分类）
     */
    Page<Goods> recommendGoods(Long userId, int page, int size);

    Goods getGoodsDetail(Long id);

    Goods getGoodsDetail(Long id, Long userId);

    Goods createGoods(Long userId, Goods goods);

    void updateGoods(Long userId, Long goodsId, Goods goods);

    void deleteGoods(Long userId, Long goodsId);

    void deleteGoods(Long userId, Long goodsId, String reason);

    /** 标记已售出 */
    void markAsSold(Long userId, Long goodsId);

    /** 擦亮商品（每天限1次） */
    void refreshGoods(Long userId, Long goodsId);

    /** 我的商品列表 */
    Page<Goods> listMyGoods(Long userId, Integer status, int page, int size);

    /** 浏览历史 */
    Page<Goods> getBrowseHistory(Long userId, int page, int size);
}
