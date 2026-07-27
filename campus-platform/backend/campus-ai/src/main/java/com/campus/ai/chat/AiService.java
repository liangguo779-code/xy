package com.campus.ai.chat;

import com.campus.ai.chat.dto.ChatRequest;
import com.campus.ai.chat.dto.ChatResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Application boundary for the AI service. The three pending/confirm methods that
 * referenced Python-side endpoints which never existed have been removed; the FastAPI
 * service never implemented them and the frontend never called them.
 */
public interface AiService {

    ChatResponse chat(ChatRequest request);

    Map<String, Object> getKnowledgeList();

    Map<String, Object> uploadKnowledge(MultipartFile file);

    Map<String, Object> rebuildKnowledge();

    Map<String, Object> getRebuildStatus();

    Map<String, Object> toggleKnowledge(String filename);

    void deleteKnowledge(String filename);

    Map<String, Object> getKnowledgeContent(String filename);

    Map<String, Object> updateKnowledge(String filename, String content);
}
