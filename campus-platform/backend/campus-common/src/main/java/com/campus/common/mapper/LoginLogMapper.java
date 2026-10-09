package com.campus.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.common.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录审计日志表 Mapper。常规 CRUD 由 MyBatis-Plus BaseMapper 提供，无自定义 SQL。
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
