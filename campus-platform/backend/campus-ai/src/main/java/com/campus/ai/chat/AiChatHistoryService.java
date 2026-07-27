package com.campus.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.ai.dto.AiChatMessageVO;
import com.campus.ai.dto.AiChatSessionVO;

import java.util.List;

public interface AiChatHistoryService {

    /** 创建会话 */
    AiChatSessionVO createSession(Long userId, String title);

    /** 获取用户会话列表 */
    List<AiChatSessionVO> getMySessions(Long userId);

    /** 保存消息 */
    void saveMessage(Long sessionId, String role, String content, String sources);

    /** 获取会话消息列表 */
    List<AiChatMessageVO> getMessages(Long sessionId, Long userId);

    /** 删除会话 */
    void deleteSession(Long userId, Long sessionId);

    /** 更新会话标题 */
    void updateTitle(Long userId, Long sessionId, String title);
}
