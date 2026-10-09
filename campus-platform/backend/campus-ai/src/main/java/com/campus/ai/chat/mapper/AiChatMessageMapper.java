package com.campus.ai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.ai.chat.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** 聊天消息表 Mapper，直接使用 MyBatis-Plus 通用 CRUD。 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
