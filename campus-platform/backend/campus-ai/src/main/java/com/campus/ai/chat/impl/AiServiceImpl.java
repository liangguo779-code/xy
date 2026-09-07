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
 * AI 服务实现：提供知识库管理（列表、上传、重建、删除、启停）等后台管理能力。
 *
 * <p>对话功能由 {@link RagOrchestrator} 处理，本类只负责知识库的 CRUD 操作。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>管理员无法上传、删除、重建知识库文档</li>
 *   <li>知识库的启停（按文件禁用/启用）功能不可用</li>
 *   <li>后台管理页面的知识库管理模块将无法正常工作</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final KnowledgeService knowledgeService;

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
