package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** AI 会话消息表 Mapper（Trade 前缀避免与其他模块重名），提供基础 CRUD */
@Mapper
public interface TradeAiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
