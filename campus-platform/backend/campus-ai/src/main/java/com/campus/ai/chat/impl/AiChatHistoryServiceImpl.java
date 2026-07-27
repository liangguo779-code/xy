package com.campus.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.ai.dto.AiChatMessageVO;
import com.campus.ai.dto.AiChatSessionVO;
import com.campus.ai.entity.AiChatMessage;
import com.campus.ai.entity.AiChatSession;
import com.campus.ai.mapper.AiChatMessageMapper;
import com.campus.ai.mapper.AiChatSessionMapper;
import com.campus.ai.service.AiChatHistoryService;
import com.campus.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatHistoryServiceImpl extends ServiceImpl<AiChatSessionMapper, AiChatSession>
        implements AiChatHistoryService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;

    @Override
    public AiChatSessionVO createSession(Long userId, String title) {
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setTitle(title != null ? title : "新对话");
        sessionMapper.insert(session);
        return toSessionVO(session);
    }

    @Override
    public List<AiChatSessionVO> getMySessions(Long userId) {
        List<AiChatSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, userId)
                        .orderByDesc(AiChatSession::getUpdateTime));
        return sessions.stream().map(this::toSessionVO).collect(Collectors.toList());
    }

    @Override
    public void saveMessage(Long sessionId, String role, String content, String sources) {
        AiChatMessage msg = new AiChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setSources(sources);
        messageMapper.insert(msg);

        // 触发表 updateTime 字段更新（@TableField(fill = INSERT_UPDATE) 才会刷新）
        AiChatSession session = new AiChatSession();
        session.setId(sessionId);
        sessionMapper.updateById(session);
    }

    @Override
    public List<AiChatMessageVO> getMessages(Long sessionId, Long userId) {
        // 校验会话归属
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此会话");
        }

        List<AiChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getCreateTime));

        return messages.stream().map(m -> {
            AiChatMessageVO vo = new AiChatMessageVO();
            vo.setId(m.getId());
            vo.setRole(m.getRole());
            vo.setContent(m.getContent());
            vo.setSources(m.getSources());
            vo.setCreateTime(m.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void deleteSession(Long userId, Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作");
        }
        // 删除消息
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId));
        // 删除会话
        sessionMapper.deleteById(sessionId);
    }

    @Override
    public void updateTitle(Long userId, Long sessionId, String title) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作");
        }
        session.setTitle(title);
        sessionMapper.updateById(session);
    }

    private AiChatSessionVO toSessionVO(AiChatSession s) {
        AiChatSessionVO vo = new AiChatSessionVO();
        vo.setId(s.getId());
        vo.setTitle(s.getTitle());
        vo.setCreateTime(s.getCreateTime());
        vo.setUpdateTime(s.getUpdateTime());
        // 获取最后一条消息预览
        AiChatMessage lastMsg = messageMapper.selectOne(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, s.getId())
                        .orderByDesc(AiChatMessage::getCreateTime)
                        .last("LIMIT 1"));
        if (lastMsg != null) {
            vo.setLastMessage(lastMsg.getContent().length() > 50
                    ? lastMsg.getContent().substring(0, 50) + "..." : lastMsg.getContent());
        }
        return vo;
    }
}
