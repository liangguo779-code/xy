package com.campus.ai.chat.impl;

import com.campus.ai.chat.dto.ChatRequest;
import com.campus.ai.chat.dto.ChatResponse;
import com.campus.ai.chat.AiService;
import com.campus.ai.knowledge.KnowledgeService;
import com.campus.ai.rag.RagOrchestrator;
import com.campus.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * In-process LangChain4j implementation of the AI service. The previous build proxied to
 * a Python service; that path is removed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final RagOrchestrator ragOrchestrator;
    private final KnowledgeService knowledgeService;

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            return ragOrchestrator.run(request);
        } catch (Exception e) {
            log.error("AI chat failed", e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public Map<String, Object> getKnowledgeList() {
        return knowledgeService.list();
    }

    @Override
    public Map<String, Object> uploadKnowledge(MultipartFile file) {
        return knowledgeService.upload(file);
    }

    @Override
    public Map<String, Object> rebuildKnowledge() {
        return knowledgeService.rebuild();
    }

    @Override
    public Map<String, Object> getRebuildStatus() {
        return knowledgeService.rebuildStatus();
    }

    @Override
    public Map<String, Object> toggleKnowledge(String filename) {
        return knowledgeService.toggle(filename);
    }

    @Override
    public void deleteKnowledge(String filename) {
        knowledgeService.delete(filename);
    }

    @Override
    public Map<String, Object> getKnowledgeContent(String filename) {
        return knowledgeService.getContent(filename);
    }

    @Override
    public Map<String, Object> updateKnowledge(String filename, String content) {
        return knowledgeService.update(filename, content);
    }
}
