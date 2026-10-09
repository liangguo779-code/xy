package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.Post;
import org.apache.ibatis.annotations.Mapper;

/** 社区帖子表 Mapper（Trade 前缀避免与其他模块重名），提供基础 CRUD */
@Mapper
public interface TradePostMapper extends BaseMapper<Post> {
}
