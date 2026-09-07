package com.campus.ai.memory;

import com.campus.ai.chat.mapper.AiChatMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.ai.chat.entity.AiChatMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话记忆存储：基于 MySQL {@code ai_chat_message} 表的读透缓存。
 *
 * <p>实现 {@link ChatMemoryStore} 接口，使 LangChain4j 的 {@link MessageWindowChatMemory}
 * 能自动加载对话历史。
 *
 * <p>持久化由 {@code AiChatController.historyService.saveMessage()} 负责，
 * 本类只做读取 + 内存缓存。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>LangChain4j 的 ChatMemory 无法从数据库加载历史，多轮对话上下文丢失</li>
 *   <li>LLM 每次回答都像新对话，无法理解"刚才说的那个""继续"等指代</li>
 *   <li>用户刷新页面后之前的对话轮次对 LLM 不可见</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryStore implements ChatMemoryStore {

    private final AiChatMessageMapper messageMapper;

    private final Map<String, List<ChatMessage>> cache = new ConcurrentHashMap<>();

    public ChatMemory getOrCreate(Long sessionId, int maxMessages) {
        String memoryId = String.valueOf(sessionId);
        if (!cache.containsKey(memoryId)) {
            cache.put(memoryId, new ArrayList<>(loadFromDb(sessionId)));
        }
        return MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .chatMemoryStore(this)
                .build();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return cache.getOrDefault(String.valueOf(memoryId), List.of());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        cache.put(String.valueOf(memoryId), new ArrayList<>(messages));
        // 持久化由 AiChatController.historyService.saveMessage() 负责
    }

    @Override
    public void deleteMessages(Object memoryId) {
        cache.remove(String.valueOf(memoryId));
    }

    public void evict(Long sessionId) {
        cache.remove(String.valueOf(sessionId));
    }

    private List<ChatMessage> loadFromDb(Long sessionId) {
        List<AiChatMessage> dbMessages = messageMapper.selectList(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getCreateTime));
        List<ChatMessage> result = new ArrayList<>();
        for (AiChatMessage m : dbMessages) {
            if ("user".equalsIgnoreCase(m.getRole())) {
                result.add(UserMessage.from(m.getContent()));
            } else if ("assistant".equalsIgnoreCase(m.getRole())) {
                result.add(AiMessage.from(m.getContent()));
            }
        }
        return result;
    }
}
