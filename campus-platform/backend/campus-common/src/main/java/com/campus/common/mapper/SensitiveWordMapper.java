package com.campus.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.common.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自定义敏感词库 Mapper。常规 CRUD 由 MyBatis-Plus BaseMapper 提供，无自定义 SQL。
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}
