package com.campus.ai.chat;

import com.campus.ai.chat.dto.ChatRequest;
import com.campus.ai.chat.dto.ChatResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * AI 服务的应用层边界接口。之前引用 Python 服务端点的三个 pending/confirm 方法已移除；
 * FastAPI 服务从未实现过它们，前端也从未调用过。
 */
public interface AiService {

    Map<String, Object> getKnowledgeList();

    Map<String, Object> uploadKnowledge(MultipartFile file);

    Map<String, Object> rebuildKnowledge();

    Map<String, Object> getRebuildStatus();

    Map<String, Object> toggleKnowledge(String filename);

    void deleteKnowledge(String filename);

    Map<String, Object> getKnowledgeContent(String filename);

    Map<String, Object> updateKnowledge(String filename, String content);
}
