package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/** 帖子评论表 Mapper（Trade 前缀避免与其他模块重名），提供基础 CRUD */
@Mapper
public interface TradeCommentMapper extends BaseMapper<Comment> {
}
