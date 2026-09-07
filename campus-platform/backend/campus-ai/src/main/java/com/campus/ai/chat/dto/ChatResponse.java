package com.campus.ai.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class ChatResponse {

    private Long sessionId;

    private String answer;

    private List<SourceItem> sources;

    /**
     * 检索置信度："high" / "medium" / "low" 之一。由 RagOrchestrator 根据最高重排序分数设置。
     * 前端可将其渲染为可信度标签。
     */
    private String confidence;

    @Data
    @Builder
    public static class SourceItem {
        private Integer index;
        private String source;
        @JsonProperty("chunk_index")
        private Integer chunkIndex;
        private String content;
        private Double score;
    }
}
