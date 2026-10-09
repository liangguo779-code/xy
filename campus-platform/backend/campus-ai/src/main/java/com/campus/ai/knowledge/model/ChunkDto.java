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
    /** 来源文件名（如"学生手册.md"），作为检索过滤与引用展示的键。 */
    private String source;
    /** 分块在同源文件内的序号，从 0 开始，与 source 组合成唯一 ID。 */
    private int chunkIndex;
    /** 分块正文内容。 */
    private String content;
    /** 分块所属章节标题（如"第三章 学籍管理"），无章节时为空。 */
    private String sectionTitle;
    /** 章节层级路径（如"第三章 > 第七条"），用于 Embedding 文本增强与来源展示。 */
    private String sectionPath;
    /** 父分块索引。-1 表示无父分块（该分块是独立分块或本身就是父分块）。 */
    @Builder.Default
    private int parentIndex = -1;
    /** 该分块是否为父分块（完整文章）。 */
    @Builder.Default
    private boolean isParent = false;
}
