package com.campus.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ChatResponse {

    private Long sessionId;

    private String answer;

    private List<SourceItem> sources;

    @Data
    public static class SourceItem {
        private Integer index;
        private String source;
        @JsonProperty("chunk_index")
        private Integer chunkIndex;
        private String content;
        private Double score;
    }
}
