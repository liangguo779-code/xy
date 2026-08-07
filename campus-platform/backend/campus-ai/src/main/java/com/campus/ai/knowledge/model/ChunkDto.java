package com.campus.ai.knowledge.model;

import lombok.Builder;
import lombok.Data;

/**
 * One chunk produced by {@link MarkdownSectionSplitter} and ingested into the vector store.
 * Supports parent-child chunking strategy for policy documents.
 *
 * <p>Parent-child chunking:
 * <ul>
 *   <li>Small chunks (≤500 chars): Used for vector search, improving recall</li>
 *   <li>Large chunks (full article): Used for generation, providing complete context</li>
 * </ul>
 */
@Data
@Builder
public class ChunkDto {
    private String source;
    private int chunkIndex;
    private String content;
    private String sectionTitle;
    private String sectionPath;
    /** Parent chunk index. -1 means no parent (this chunk is either standalone or a parent). */
    @Builder.Default
    private int parentIndex = -1;
    /** Whether this chunk is a parent chunk (full article). */
    @Builder.Default
    private boolean isParent = false;
}
