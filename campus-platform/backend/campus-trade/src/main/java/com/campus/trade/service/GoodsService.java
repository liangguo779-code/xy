package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Goods;

import java.math.BigDecimal;

/** 商品服务，负责商品发布/检索/推荐及上下架、擦亮等生命周期管理 */
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

    /** 商品详情（不记录浏览历史） */
    Goods getGoodsDetail(Long id);

    /** 商品详情并记录浏览历史，用于"最近浏览" */
    Goods getGoodsDetail(Long id, Long userId);

    /** 发布商品 */
    Goods createGoods(Long userId, Goods goods);

    /** 编辑商品（仅发布者本人可改） */
    void updateGoods(Long userId, Long goodsId, Goods goods);

    /** 下架/删除商品 */
    void deleteGoods(Long userId, Long goodsId);

    /** 下架商品并记录下架原因 */
    void deleteGoods(Long userId, Long goodsId, String reason);

    /** 标记已售出 */
    void markAsSold(Long userId, Long goodsId);

    /** 擦亮商品（每天限1次） */
    void refreshGoods(Long userId, Long goodsId);

    /** 重新上架（已下架的商品） */
    void reshelfGoods(Long userId, Long goodsId);

    /** 我的商品列表 */
    Page<Goods> listMyGoods(Long userId, Integer status, int page, int size);

    /** 浏览历史 */
    Page<Goods> getBrowseHistory(Long userId, int page, int size);
}
