package com.campus.ai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.ai.chat.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
