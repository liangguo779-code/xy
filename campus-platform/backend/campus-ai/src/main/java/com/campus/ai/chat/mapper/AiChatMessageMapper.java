package com.campus.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.ai.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
