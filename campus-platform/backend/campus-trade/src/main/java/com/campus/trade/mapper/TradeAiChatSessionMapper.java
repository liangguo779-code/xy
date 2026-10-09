package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.AiChatSession;
import org.apache.ibatis.annotations.Mapper;

/** AI 对话会话表 Mapper（Trade 前缀避免与其他模块重名），提供基础 CRUD */
@Mapper
public interface TradeAiChatSessionMapper extends BaseMapper<AiChatSession> {
}
