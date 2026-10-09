package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.Goods;
import org.apache.ibatis.annotations.Mapper;

/** 商品表 Mapper，提供基础 CRUD */
@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {
}
