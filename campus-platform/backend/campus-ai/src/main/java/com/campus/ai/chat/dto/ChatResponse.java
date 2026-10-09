package com.campus.ai.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** AI 对话响应体。 */
@Data
public class ChatResponse {

    /** 会话 ID（新建会话时由服务端回填）。 */
    private Long sessionId;

    /** 生成的回答文本。 */
    private String answer;

    /** 引用来源列表，index 与回答中的 [来源N] 对应。 */
    private List<SourceItem> sources;

    /**
     * 检索置信度："high" / "medium" / "low" 之一。由 RagOrchestrator 根据最高重排序分数设置。
     * 前端可将其渲染为可信度标签。
     */
    private String confidence;

    /** 单条引用来源。 */
    @Data
    @Builder
    public static class SourceItem {
        /** 来源编号，从 1 开始，对应正文中的 [来源N]。 */
        private Integer index;
        /** 来源展示名（章节路径优先，否则为文件名）。 */
        private String source;
        /** 分块在源文件内的序号。 */
        @JsonProperty("chunk_index")
        private Integer chunkIndex;
        /** 分块正文片段。 */
        private String content;
        /** 检索相关性分数。 */
        private Double score;
    }
}
