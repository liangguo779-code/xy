package com.campus.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.common.entity.BanRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 封禁记录表 Mapper。常规 CRUD 由 MyBatis-Plus BaseMapper 提供，无自定义 SQL。
 */
@Mapper
public interface BanRecordMapper extends BaseMapper<BanRecord> {
}
