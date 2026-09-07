package com.campus.ai.memory;

import com.campus.ai.chat.mapper.AiChatMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.ai.chat.entity.AiChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 对话记忆存储：基于 Redis 的分布式读透缓存，MySQL 为持久层。
 *
 * <p>实现 {@link ChatMemoryStore} 接口，使 LangChain4j 的 {@link MessageWindowChatMemory}
 * 能自动加载对话历史。
 *
 * <p>缓存策略：
 * <ul>
 *   <li>Redis Key：{@code ai:chat:memory:{sessionId}}，值为 JSON 数组</li>
 *   <li>TTL：30 分钟滑动过期（每次读写刷新）</li>
 *   <li>Cache miss 时从 MySQL 加载并写入 Redis</li>
 *   <li>支持多实例部署，缓存天然共享</li>
 * </ul>
 *
 * <p>持久化由 {@code AiChatController.historyService.saveMessage()} 负责，
 * 本类只做读取 + Redis 缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryStore implements ChatMemoryStore {

    private final AiChatMessageMapper messageMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "ai:chat:memory:";
    private static final long TTL_MINUTES = 30;

    public ChatMemory getOrCreate(Long sessionId, int maxMessages) {
        String memoryId = String.valueOf(sessionId);
        String key = KEY_PREFIX + memoryId;

        // 先查 Redis，miss 时从 MySQL 加载
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            List<ChatMessage> dbMessages = loadFromDb(sessionId);
            putToRedis(key, dbMessages);
        } else {
            // 刷新 TTL（滑动过期）
            redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
        }

        return MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .chatMemoryStore(this)
                .build();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            // Redis 中不存在，从 MySQL 加载
            List<ChatMessage> dbMessages = loadFromDb(Long.parseLong(String.valueOf(memoryId)));
            putToRedis(key, dbMessages);
            return dbMessages;
        }
        // 刷新 TTL
        redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
        return deserializeMessages(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = KEY_PREFIX + memoryId;
        putToRedis(key, new ArrayList<>(messages));
        // 持久化由 AiChatController.historyService.saveMessage() 负责
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
        redisTemplate.delete(key);
    }

    /**
     * 从外部清除指定会话的缓存（删除会话时调用）。
     */
    public void evict(Long sessionId) {
        String key = KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
        log.debug("Evicted Redis cache for session {}", sessionId);
    }

    // ── 内部方法 ─────────────────────────────────────────────────────────────────

    private void putToRedis(String key, List<ChatMessage> messages) {
        try {
            String json = serializeMessages(messages);
            redisTemplate.opsForValue().set(key, json, TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to write chat memory to Redis: {}", e.getMessage());
        }
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

    // ── 序列化 ───────────────────────────────────────────────────────────────────

    /**
     * 将 ChatMessage 列表序列化为 JSON 数组。
     * 格式：[{"type":"USER","text":"..."},{"type":"AI","text":"..."}]
     */
    private String serializeMessages(List<ChatMessage> messages) {
        List<Map<String, String>> list = new ArrayList<>(messages.size());
        for (ChatMessage msg : messages) {
            Map<String, String> entry = new HashMap<>(2);
            if (msg instanceof UserMessage um) {
                entry.put("type", "USER");
                entry.put("text", um.singleText());
            } else if (msg instanceof AiMessage am) {
                entry.put("type", "AI");
                entry.put("text", am.text());
            } else {
                // 其他类型（SystemMessage 等）跳过，对话场景不需要
                continue;
            }
            list.add(entry);
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize chat messages: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 从 JSON 数组反序列化为 ChatMessage 列表。
     */
    private List<ChatMessage> deserializeMessages(String json) {
        try {
            List<Map<String, String>> list = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, String>>>() {});
            List<ChatMessage> result = new ArrayList<>(list.size());
            for (Map<String, String> entry : list) {
                String type = entry.get("type");
                String text = entry.get("text");
                if ("USER".equals(type)) {
                    result.add(UserMessage.from(text));
                } else if ("AI".equals(type)) {
                    result.add(AiMessage.from(text));
                }
            }
            return result;
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize chat messages from Redis: {}", e.getMessage());
            return List.of();
        }
    }
}
