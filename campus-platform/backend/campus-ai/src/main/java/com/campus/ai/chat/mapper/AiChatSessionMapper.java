package com.campus.ai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.ai.chat.entity.AiChatSession;
import org.apache.ibatis.annotations.Mapper;

/** 聊天会话表 Mapper，直接使用 MyBatis-Plus 通用 CRUD。 */
@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {
}
