package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/** 聊天会话表 Mapper，提供基础 CRUD */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
