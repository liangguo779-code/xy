package com.campus.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.forum.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/** 评论表 Mapper，提供 MyBatis-Plus 通用 CRUD */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
