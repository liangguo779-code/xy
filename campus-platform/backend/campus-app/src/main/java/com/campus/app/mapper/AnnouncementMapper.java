package com.campus.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.app.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;

/** 公告表 Mapper，通用 CRUD 由 MyBatis-Plus BaseMapper 提供 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {
}
