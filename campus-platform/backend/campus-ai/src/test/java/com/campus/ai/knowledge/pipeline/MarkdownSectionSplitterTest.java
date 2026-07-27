package com.campus.ai.knowledge.pipeline;

import com.campus.ai.knowledge.model.ChunkDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownSectionSplitterTest {

    private final MarkdownSectionSplitter splitter = new MarkdownSectionSplitter();

    @Test
    void splitsOnTopLevelHeadings() {
        String md = "# A\n\nalpha body\n\n# B\n\nbeta body\n";
        List<ChunkDto> chunks = splitter.split("test.md", md, 500, 50);
        assertEquals(2, chunks.size());
        assertEquals("A", chunks.get(0).getSectionTitle());
        assertEquals("B", chunks.get(1).getSectionTitle());
        assertTrue(chunks.get(0).getContent().contains("alpha body"));
        assertTrue(chunks.get(1).getContent().contains("beta body"));
    }

    @Test
    void buildsSectionPathFromHierarchy() {
        // # Top body spans ## Mid body and ### Leaf body — the body for Top is everything
        // until the next SAME-level heading. The result: Top gets 1 chunk (the inner body),
        // Mid gets 1 chunk ("inner"), Leaf gets 1 chunk ("deep") = 3 chunks total.
        String md = "# Top\n\n## Mid\n\ninner\n\n### Leaf\n\ndeep\n";
        List<ChunkDto> chunks = splitter.split("test.md", md, 500, 50);
        assertTrue(chunks.size() >= 2, "Should have at least 2 chunks");
        // Check that "inner" and "deep" content appear
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
