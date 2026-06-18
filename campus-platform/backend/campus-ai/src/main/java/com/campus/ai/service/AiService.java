package com.campus.ai.service;

import com.campus.ai.dto.ChatRequest;
import com.campus.ai.dto.ChatResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface AiService {

    ChatResponse chat(ChatRequest request);

    Map<String, Object> getKnowledgeList();

    Map<String, Object> uploadKnowledge(MultipartFile file);

    Map<String, Object> rebuildKnowledge();

    Map<String, Object> getPendingList();

    Map<String, Object> confirmKnowledge(String filename);

    Map<String, Object> toggleKnowledge(String filename);

    void deleteKnowledge(String filename);

    void deletePending(String filename);

    Map<String, Object> getKnowledgeContent(String filename);

    Map<String, Object> updateKnowledge(String filename, String content);
}
