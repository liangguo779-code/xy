package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.entity.Favorite;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.FavoriteMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements GoodsService {

    private final GoodsMapper goodsMapper;
    private final FavoriteMapper favoriteMapper;

    @Override
    public Page<Goods> listGoods(String keyword, Long categoryId, Integer type,
                                 BigDecimal minPrice, BigDecimal maxPrice,
                                 String condition, String sortBy,
                                 int page, int size) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0);

        if (type != null) {
            wrapper.eq(Goods::getType, type);
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Goods::getTitle, keyword)
                    .or().like(Goods::getDescription, keyword));
        }

        if (categoryId != null) {
            wrapper.eq(Goods::getCategoryId, categoryId);
        }

        if (minPrice != null) {
            wrapper.ge(Goods::getPrice, minPrice);
        }
        if (maxPrice != null) {
            wrapper.le(Goods::getPrice, maxPrice);
        }

        if (StringUtils.hasText(condition)) {
            wrapper.eq(Goods::getCondition, condition);
        }

        // 排序
        if ("price_asc".equals(sortBy)) {
            wrapper.orderByAsc(Goods::getPrice);
        } else if ("price_desc".equals(sortBy)) {
            wrapper.orderByDesc(Goods::getPrice);
        } else if ("hottest".equals(sortBy)) {
            wrapper.orderByDesc(Goods::getViewCount);
        } else {
            // 默认按最新
            wrapper.orderByDesc(Goods::getCreateTime);
        }

        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Page<Goods> recommendGoods(int page, int size) {
        return recommendGoods(null, page, size);
    }

    @Override
    public Page<Goods> recommendGoods(Long userId, int page, int size) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0)
                .eq(Goods::getType, 0);

        // 个性化推荐：基于用户收藏的商品分类
        if (userId != null) {
            List<Long> favGoodsIds = favoriteMapper.selectList(
                    new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId))
                    .stream().map(Favorite::getGoodsId).collect(Collectors.toList());

            if (!favGoodsIds.isEmpty()) {
                List<Goods> favGoods = goodsMapper.selectBatchIds(favGoodsIds);
                List<Long> categoryIds = favGoods.stream()
                        .map(Goods::getCategoryId).filter(java.util.Objects::nonNull)
                        .distinct().collect(Collectors.toList());

                if (!categoryIds.isEmpty()) {
                    wrapper.in(Goods::getCategoryId, categoryIds);
                }
            }
        }

        wrapper.orderByDesc(Goods::getViewCount).orderByDesc(Goods::getWantCount);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Goods getGoodsDetail(Long id) {
        Goods goods = getById(id);
        if (goods == null) {
            throw new BusinessException("商品不存在");
        }
        update(new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .setSql("view_count = view_count + 1"));
        return goods;
    }

    @Override
    public Goods createGoods(Long userId, Goods goods) {
        // 强制设置字段，防止前端篡改
        goods.setUserId(userId);
        goods.setStatus(0);  // 始终为已上架
        if (goods.getType() == null) {
            goods.setType(0);
        }
        goods.setViewCount(0);
        goods.setWantCount(0);
        goods.setLikeCount(0);
        save(goods);
        return goods;
    }

    @Override
    public void updateGoods(Long userId, Long goodsId, Goods goods) {
        Goods existing = getById(goodsId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        // 只允许修改特定字段，防止篡改归属和状态
        existing.setTitle(goods.getTitle());
        existing.setDescription(goods.getDescription());
        existing.setPrice(goods.getPrice());
        existing.setOriginalPrice(goods.getOriginalPrice());
        existing.setCategory(goods.getCategory());
        existing.setCategoryId(goods.getCategoryId());
        existing.setCondition(goods.getCondition());
        existing.setImages(goods.getImages());
        existing.setVideoUrl(goods.getVideoUrl());
        updateById(existing);
    }

    @Override
    public void deleteGoods(Long userId, Long goodsId) {
        deleteGoods(userId, goodsId, null);
    }

    public void deleteGoods(Long userId, Long goodsId, String reason) {
        Goods existing = getById(goodsId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        if (existing.getStatus() != 0) {
            throw new BusinessException("商品不在售状态，无法下架");
        }
        update(new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .set(Goods::getStatus, 1)
                .set(Goods::getOffReason, reason));
    }

    @Override
    public void markAsSold(Long userId, Long goodsId) {
        Goods existing = getById(goodsId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        if (existing.getStatus() != 0) {
            throw new BusinessException("商品不在售状态");
        }
        update(new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .set(Goods::getStatus, 2));
    }

    @Override
    public Page<Goods> listMyGoods(Long userId, Integer status, int page, int size) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getUserId, userId);
        if (status != null) {
            wrapper.eq(Goods::getStatus, status);
        }
        wrapper.orderByDesc(Goods::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }
}
