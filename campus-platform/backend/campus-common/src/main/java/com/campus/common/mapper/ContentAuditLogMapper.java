package com.campus.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.common.entity.ContentAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容安全审计流水 Mapper。常规 CRUD 由 MyBatis-Plus BaseMapper 提供，无自定义 SQL。
 */
@Mapper
public interface ContentAuditLogMapper extends BaseMapper<ContentAuditLog> {
}
