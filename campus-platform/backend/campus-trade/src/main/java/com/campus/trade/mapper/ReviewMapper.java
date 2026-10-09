package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.Review;
import org.apache.ibatis.annotations.Mapper;

/** 订单评价表 Mapper，提供基础 CRUD */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
}
