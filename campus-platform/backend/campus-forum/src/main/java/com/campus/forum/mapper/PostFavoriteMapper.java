package com.campus.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.forum.entity.PostFavorite;
import org.apache.ibatis.annotations.Mapper;

/** 帖子收藏表 Mapper，提供 MyBatis-Plus 通用 CRUD */
@Mapper
public interface PostFavoriteMapper extends BaseMapper<PostFavorite> {
}
