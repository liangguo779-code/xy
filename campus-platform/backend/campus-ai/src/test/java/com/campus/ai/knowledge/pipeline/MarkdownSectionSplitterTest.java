package com.campus.ai.knowledge.pipeline;

import com.campus.ai.knowledge.model.ChunkDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** MarkdownSectionSplitter 分块行为测试：标题切分、层级路径、无标题兜底、超长再切、目录剥离。 */
class MarkdownSectionSplitterTest {

    private final MarkdownSectionSplitter splitter = new MarkdownSectionSplitter();

    @Test
    void splitsOnTopLevelHeadings() {
        // 正文需 ≥ MIN_CHUNK_SIZE（30 字符），否则按"碎块无检索价值"被丢弃。
        String md = "# A\n\nalpha body with enough characters to survive the minimum chunk filter.\n\n# B\n\nbeta body with enough characters to survive the minimum chunk filter.\n";
        List<ChunkDto> chunks = splitter.split("test.md", md, 500, 50);
        assertEquals(2, chunks.size());
        assertEquals("A", chunks.get(0).getSectionTitle());
        assertEquals("B", chunks.get(1).getSectionTitle());
        assertTrue(chunks.get(0).getContent().contains("alpha body"));
        assertTrue(chunks.get(1).getContent().contains("beta body"));
    }

    @Test
    void buildsSectionPathFromHierarchy() {
        // 每个标题的 body 截止到下一个标题（任意层级）：
        // Top 的 body 为空（其后紧跟 ## Mid）不产生分块，Mid 与 Leaf 各产生 1 个分块。
        // sectionPath 路径锚定最近的 # 顶级标题向下拼接：Top / Mid、Top / Mid / Leaf。
        String md = "# Top\n\n## Mid\n\ninner content that is long enough to survive the minimum chunk filter.\n\n### Leaf\n\ndeep content that is long enough to survive the minimum chunk filter.\n";
        List<ChunkDto> chunks = splitter.split("test.md", md, 500, 50);
        assertEquals(2, chunks.size());
        assertEquals("Top / Mid", chunks.get(0).getSectionPath());
        assertEquals("Top / Mid / Leaf", chunks.get(1).getSectionPath());
        boolean hasInner = chunks.stream().anyMatch(c -> c.getContent().contains("inner"));
        boolean hasDeep = chunks.stream().anyMatch(c -> c.getContent().contains("deep"));
        assertTrue(hasInner, "inner content should appear");
        assertTrue(hasDeep, "deep content should appear");
    }

    @Test
    void fallsBackToRootSectionWhenNoHeadings() {
        String md = "no headings here, just a paragraph.\n\nAnother paragraph.";
        List<ChunkDto> chunks = splitter.split("test.md", md, 500, 50);
        assertEquals(1, chunks.size());
        assertEquals("__root__", chunks.get(0).getSectionTitle());
        assertTrue(chunks.get(0).getContent().contains("no headings here"));
    }

    @Test
    void subSplitsLargeBody() {
        StringBuilder big = new StringBuilder("# Big\n\n");
        for (int i = 0; i < 50; i++) {
            big.append("paragraph ").append(i).append(" with some content.\n\n");
        }
        List<ChunkDto> chunks = splitter.split("test.md", big.toString(), 200, 20);
        assertTrue(chunks.size() > 1, "Large body must produce multiple chunks");
        for (ChunkDto c : chunks) {
            assertNotNull(c.getContent());
            assertFalse(c.getContent().isEmpty());
        }
    }

    @Test
    void emptyMarkdownProducesNoChunks() {
        assertTrue(splitter.split("test.md", "", 500, 50).isEmpty());
        assertTrue(splitter.split("test.md", null, 500, 50).isEmpty());
    }

    @Test
    void overlapsTailBetweenOversizedChunks() {
        // 超长单段落走 splitBySize 路径：相邻子分块应共享上一块尾部 chunkOverlap 个字符。
        // 用无标题文档（__root__ 路径无章节前缀），子分块内容与 splitBySize 输出一一对应。
        StringBuilder para = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            para.append("这是用于测试分块重叠的长句编号").append(i).append("，句子内容足够长以便按句切分。");
        }
        int chunkSize = 120;
        int overlap = 30;
        List<ChunkDto> chunks = splitter.split("test.md", para.toString(), chunkSize, overlap);
        List<ChunkDto> children = chunks.stream().filter(c -> !c.isParent()).toList();
        assertTrue(children.size() >= 2, "超长段落应被切成多个子分块");
        for (int i = 0; i + 1 < children.size(); i++) {
            String cur = children.get(i).getContent();
            String next = children.get(i + 1).getContent();
            int ov = Math.min(overlap, cur.length());
            String tail = cur.substring(cur.length() - ov);
            assertTrue(next.startsWith(tail),
                    "子分块 " + (i + 1) + " 应以上一分块尾部 " + ov + " 字符开头");
        }
    }

    @Test
    void keepsTinyChunksDiscarded() {
        // 重叠不会救活碎块：正文 < 30 字符的分块仍被丢弃。
        String md = "# A\n\nshort body\n";
        assertTrue(splitter.split("test.md", md, 500, 50).isEmpty());
    }

    @Test
    void tocSectionIsStrippedBeforeChunking() {
        String md = "# Handbook\n\n## Preface\n\npreface body\n\n## Contents\n\n- Chapter A\n- Chapter B\n- Chapter C\n\n## Chapter A\n\nactual content for chapter A\n\n## Chapter B\n\nactual content for chapter B\n";
        List<ChunkDto> chunks = splitter.split("test.md", md, 800, 50);
        // Debug: print chunks
        System.out.println("=== chunks: " + chunks.size());
        for (ChunkDto c : chunks) {
            System.out.println("  chunk=" + c.getChunkIndex() + " path=" + c.getSectionPath() + " content=" + c.getContent().replace("\n", " | ").substring(0, Math.min(80, c.getContent().length())));
        }
        // Should NOT have chunks containing "- Chapter A" (TOC items)
        for (ChunkDto c : chunks) {
            assertFalse(c.getContent().contains("- Chapter A"), "TOC item should not appear in chunks");
            assertFalse(c.getContent().contains("- Chapter B"), "TOC item should not appear in chunks");
        }
        // Preface + Chapter A + Chapter B should all still be present
        boolean hasPreface = chunks.stream().anyMatch(c -> c.getContent().contains("preface body"));
        boolean hasChapterA = chunks.stream().anyMatch(c -> c.getContent().contains("actual content for chapter A"));
        boolean hasChapterB = chunks.stream().anyMatch(c -> c.getContent().contains("actual content for chapter B"));
        assertTrue(hasPreface, "Preface body should remain");
        assertTrue(hasChapterA, "Chapter A content should remain");
        assertTrue(hasChapterB, "Chapter B content should remain");
    }

    @Test
    void chineseTocHeadingIsStripped() {
        String md = "# 标题\n\n## 前言\n\n前言内容\n\n## 目录\n\n### 行为规定\n- 学生行为准则\n- 学生管理规定\n\n## 第一章 总则\n\n**第一条** 总则内容\n\n## 第二章 学籍注册\n\n**第二条** 注册内容\n";
        List<ChunkDto> chunks = splitter.split("test.md", md, 800, 50);
        // Debug
        System.out.println("chineseToc: chunks=" + chunks.size());
        for (ChunkDto c : chunks) {
            System.out.println("  " + c.getChunkIndex() + " path=" + c.getSectionPath() + " content=" + c.getContent().replace("\n", " | ").substring(0, Math.min(60, c.getContent().length())));
        }
        // Actual content should survive
        boolean hasChapter1 = chunks.stream().anyMatch(c -> c.getContent().contains("总则内容"));
        boolean hasChapter2 = chunks.stream().anyMatch(c -> c.getContent().contains("注册内容"));
        assertTrue(hasChapter1, "Chapter 1 content should remain");
        assertTrue(hasChapter2, "Chapter 2 content should remain");
    }
}
