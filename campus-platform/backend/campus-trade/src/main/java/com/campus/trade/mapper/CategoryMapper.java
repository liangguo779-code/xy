package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/** 商品分类表 Mapper，提供基础 CRUD */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
