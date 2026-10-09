package com.campus.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.app.entity.CrazyThursday;
import org.apache.ibatis.annotations.Mapper;

/** 疯狂星期四活动表 Mapper，通用 CRUD 由 MyBatis-Plus BaseMapper 提供 */
@Mapper
public interface CrazyThursdayMapper extends BaseMapper<CrazyThursday> {
}
