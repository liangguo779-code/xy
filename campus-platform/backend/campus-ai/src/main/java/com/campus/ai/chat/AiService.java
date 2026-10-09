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

    /** 知识库文件列表。 */
    Map<String, Object> getKnowledgeList();

    /** 上传知识文件并异步入库。 */
    Map<String, Object> uploadKnowledge(MultipartFile file);

    /** 触发全量重建索引。 */
    Map<String, Object> rebuildKnowledge();

    /** 查询重建进度。 */
    Map<String, Object> getRebuildStatus();

    /** 切换文件启用/禁用。 */
    Map<String, Object> toggleKnowledge(String filename);

    /** 删除文件及其索引。 */
    void deleteKnowledge(String filename);

    /** 读取文件内容（转 Markdown）。 */
    Map<String, Object> getKnowledgeContent(String filename);

    /** 更新文件内容并重新入库。 */
    Map<String, Object> updateKnowledge(String filename, String content);
}
