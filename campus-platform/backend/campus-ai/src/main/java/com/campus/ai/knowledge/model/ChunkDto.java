package com.campus.ai.knowledge.model;

import lombok.Builder;
import lombok.Data;

/**
 * One chunk produced by {@link MarkdownSectionSplitter} and ingested into the vector store.
 * Mirrors the keys used by the Python pipeline (source, chunk_index, section_title, section_path).
 */
@Data
@Builder
public class ChunkDto {
    private String source;
    private int chunkIndex;
    private String content;
    private String sectionTitle;
    private String sectionPath;
}
