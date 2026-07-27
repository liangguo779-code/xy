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
        String md = "# Top\n\n## Mid\n\ninner\n\n### Leaf\n\ndeep\n";
        List<ChunkDto> chunks = splitter.split("test.md", md, 500, 50);
        assertEquals(3, chunks.size());
        assertEquals("Top", chunks.get(0).getSectionTitle());
        assertEquals("Top", chunks.get(0).getSectionPath());
        assertEquals("Mid", chunks.get(1).getSectionTitle());
        assertEquals("Top / Mid", chunks.get(1).getSectionPath());
        assertEquals("Leaf", chunks.get(2).getSectionTitle());
        assertEquals("Top / Mid / Leaf", chunks.get(2).getSectionPath());
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
}
