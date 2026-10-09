package com.campus.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.common.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知表 Mapper。常规 CRUD 由 MyBatis-Plus BaseMapper 提供，无自定义 SQL。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
