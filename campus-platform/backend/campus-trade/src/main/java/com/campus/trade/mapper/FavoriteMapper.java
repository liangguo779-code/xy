package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/** 商品收藏表 Mapper，提供基础 CRUD */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
