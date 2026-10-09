package com.campus.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.forum.entity.Post;
import org.apache.ibatis.annotations.Mapper;

/** 帖子表 Mapper，提供 MyBatis-Plus 通用 CRUD */
@Mapper
public interface PostMapper extends BaseMapper<Post> {
}
