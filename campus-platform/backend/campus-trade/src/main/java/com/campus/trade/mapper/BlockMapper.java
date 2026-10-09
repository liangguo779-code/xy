package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.Block;
import org.apache.ibatis.annotations.Mapper;

/** 用户拉黑表 Mapper，提供基础 CRUD */
@Mapper
public interface BlockMapper extends BaseMapper<Block> {
}
