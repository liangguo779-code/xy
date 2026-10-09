package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** 聊天消息表 Mapper，提供基础 CRUD */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
