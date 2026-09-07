package com.campus.ai.knowledge.model;

import lombok.Builder;
import lombok.Data;

/**
 * 由 {@link MarkdownSectionSplitter} 生成的单个分块，写入向量存储。
 * 支持政策文档的父子分块策略。
 *
 * <p>父子分块机制：
 * <ul>
 *   <li>小分块（≤500 字符）：用于向量检索，提升召回率</li>
 *   <li>大分块（完整文章）：用于生成回答，提供完整上下文</li>
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
    /** 父分块索引。-1 表示无父分块（该分块是独立分块或本身就是父分块）。 */
    @Builder.Default
    private int parentIndex = -1;
    /** 该分块是否为父分块（完整文章）。 */
    @Builder.Default
    private boolean isParent = false;
}
