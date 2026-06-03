package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.entity.Favorite;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.FavoriteMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    private final GoodsMapper goodsMapper;

    @Override
    @Transactional
    public void addFavorite(Long userId, Long goodsId) {
        Long count = count(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getGoodsId, goodsId));
        if (count > 0) return;

        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setGoodsId(goodsId);
        save(fav);

        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId).setSql("like_count = like_count + 1"));
    }

    @Override
    @Transactional
    public void removeFavorite(Long userId, Long goodsId) {
        remove(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getGoodsId, goodsId));

        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId).setSql("like_count = GREATEST(like_count - 1, 0)"));
    }

    @Override
    public boolean isFavorited(Long userId, Long goodsId) {
        return count(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getGoodsId, goodsId)) > 0;
    }

    @Override
    public List<Goods> getMyFavorites(Long userId) {
        List<Favorite> favs = list(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).orderByDesc(Favorite::getCreateTime));
        List<Long> goodsIds = favs.stream().map(Favorite::getGoodsId).collect(Collectors.toList());
        if (goodsIds.isEmpty()) return List.of();
        return goodsMapper.selectBatchIds(goodsIds);
    }
}
