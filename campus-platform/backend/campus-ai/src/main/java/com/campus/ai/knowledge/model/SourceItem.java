package com.campus.ai.knowledge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * A single citation attached to a chat answer. Shape is identical to the old Python service's
 * source item so the frontend (frontend/src/api/ai.js) continues to render it without changes.
 */
@Data
@Builder
public class SourceItem {
    private Integer index;
    private String source;
    @JsonProperty("chunk_index")
    private Integer chunkIndex;
    private String content;
    private Double score;
}
