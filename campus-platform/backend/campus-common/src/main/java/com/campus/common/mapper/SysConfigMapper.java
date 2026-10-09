package com.campus.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.common.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置表 Mapper。常规 CRUD 由 MyBatis-Plus BaseMapper 提供，无自定义 SQL。
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
