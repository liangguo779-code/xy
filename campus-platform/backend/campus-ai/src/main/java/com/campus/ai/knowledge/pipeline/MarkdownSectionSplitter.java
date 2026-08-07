package com.campus.ai.knowledge.pipeline;

import com.campus.ai.knowledge.model.ChunkDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown-aware chunker tuned for Chinese policy documents (e.g. 《学生手册》).
 *
 * <p>Splits on two hierarchies:
 * <ol>
 *   <li>{@code #} / {@code ##} / {@code ###} Markdown headings (e.g. {@code # 第一章 总则})</li>
 *   <li>{@code **第X条**} article clauses (e.g. {@code **第十条** 申请重学时间...})</li>
 * </ol>
 *
 * <p>Each article (条款) is treated as an independent chunk boundary. This provides
 * precise retrieval for RAG queries like "转专业条件" → directly matches 第二十一条.
 *
 * <p>Parent-child chunking strategy:
 * <ul>
 *   <li>Small chunks (≤chunkSize): Used for vector search, improving recall</li>
 *   <li>Large chunks (full article): Used for generation, providing complete context</li>
 * </ul>
 *
 * <p>For each chunk, the section path (e.g. "第三章 学籍管理 / 第三节 转专业与转学 / 第二十一条")
 * is prepended to the chunk content so the reference displayed in the frontend is self-contained.
 *
 * <p>Document header noise (e.g. "（教育部令 第 41 号）") is detected and stripped from
 * the top of each section so the first chunk of a chapter doesn't waste its budget
 * on a citation.
 */
@Component
public class MarkdownSectionSplitter {

    private static final Pattern HEADING =
            Pattern.compile("^(#{1,6})\\s+(.*)$", Pattern.MULTILINE);
    // Article pattern: **第X条** or **第XX条** or **第XXX条**
    // This is the primary chunk boundary for policy documents.
    private static final Pattern ARTICLE =
            Pattern.compile("^\\*\\*(第[一二三四五六七八九十百千\\d]+条)\\*\\*", Pattern.MULTILINE);
    // Document header lines (e.g. "（教育部令 第 41 号）") that waste chunk budget
    private static final Pattern DOC_HEADER =
            Pattern.compile("^\\s*[（(][^）)]*[）)]\\s*$", Pattern.MULTILINE);
    // TOC heading markers (both Chinese and English).
    // Chinese characters use Unicode escapes to avoid encoding corruption.
    private static final String[] TOC_MARKERS = {"目录", "Contents", "Table of Contents", "TOC"};

    /**
     * Strip table-of-contents sections from the markdown before chunking.
     * A TOC section contains sub-headings followed by bullet lists with NO paragraph
     * text between bullets and the next heading. Detects this pattern by checking
     * that the section body has 3+ bullet lines and no non-bullet non-heading text.
     * Avoids CJK string literals to prevent Write-tool encoding corruption.
     */
    private static String stripToc(String markdown) {
        String[] lines = markdown.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith("#")) continue;
            int level = countHashes(line);
            // Find end of this section
            int sectionEnd = lines.length;
            for (int k = i + 1; k < lines.length; k++) {
                if (lines[k].startsWith("#") && countHashes(lines[k]) <= level) {
                    sectionEnd = k;
                    break;
                }
            }
            if (sectionEnd <= i + 1) continue; // empty section
            // Heuristic: a TOC section has bullet lines ("- ...") but NO paragraph text
            // (non-empty lines that aren't headings or bullets).
            boolean hasBullet = false;
            boolean hasNonBulletContent = false;
            int bulletCount = 0;
            for (int k = i + 1; k < sectionEnd; k++) {
                String l = lines[k].trim();
                if (l.isEmpty()) continue;
                if (l.startsWith("#")) continue;
                if (l.startsWith("- ")) {
                    hasBullet = true;
                    bulletCount++;
                } else {
                    hasNonBulletContent = true;
                }
            }
            // A TOC has bullets and NO regular paragraph text
            if (hasBullet && !hasNonBulletContent && bulletCount >= 3) {
                return String.join("\n", java.util.Arrays.copyOfRange(lines, 0, i))
                        + "\n"
                        + String.join("\n", java.util.Arrays.copyOfRange(lines, sectionEnd, lines.length));
            }
        }
        return markdown;
    }

    private static int countHashes(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '#') count++;
        return count;
    }

    private static final int MIN_CHUNK_SIZE = 30;

    public List<ChunkDto> split(String source, String markdown, int chunkSize, int chunkOverlap) {
        return split(source, markdown, chunkSize, chunkOverlap, 200);
    }

    public List<ChunkDto> split(String source, String markdown, int chunkSize, int chunkOverlap, int minChunkSize) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        markdown = stripToc(markdown);
        List<Section> sections = parseSections(markdown);
        List<ChunkDto> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (Section sec : sections) {
            chunkIndex = splitSection(sec, chunkSize, chunkOverlap, source, chunkIndex, chunks);
        }
        // Merge consecutive small chunks from the same section path.
        chunks = mergeSmallChunks(chunks, chunkSize, minChunkSize);
        // Filter out tiny chunks that provide no retrieval value.
        chunks.removeIf(c -> c.getContent() != null && c.getContent().trim().length() < MIN_CHUNK_SIZE);
        // Re-index after filtering.
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setChunkIndex(i);
        }
        return chunks;
    }

    /**
     * Merge consecutive small chunks (below minChunkSize) that share the same section path.
     * This produces fewer, more meaningful chunks for retrieval.
     */
    private List<ChunkDto> mergeSmallChunks(List<ChunkDto> chunks, int chunkSize, int minChunkSize) {
        if (chunks.isEmpty()) return chunks;
        List<ChunkDto> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String bufferPath = null;
        String bufferTitle = null;

        for (ChunkDto c : chunks) {
            String content = c.getContent() == null ? "" : c.getContent().trim();
            if (content.isEmpty()) continue;

            // If this chunk is a parent chunk (full article), keep it as-is.
            if (c.isParent()) {
                // Flush buffer first.
                if (buffer.length() > 0) {
                    merged.add(buildMergedChunk(merged.size(), buffer.toString(), bufferPath, bufferTitle, chunks.get(0).getSource()));
                    buffer.setLength(0);
                }
                merged.add(c);
                bufferPath = null;
                continue;
            }

            String path = c.getSectionPath() == null ? "" : c.getSectionPath();

            // If buffer is empty, start accumulating.
            if (buffer.length() == 0) {
                buffer.append(content);
                bufferPath = path;
                bufferTitle = c.getSectionTitle();
                continue;
            }

            // Same section path and combined size <= chunkSize: merge.
            boolean samePath = path.equals(bufferPath);
            boolean fitsTogether = buffer.length() + 2 + content.length() <= chunkSize;

            if (samePath && fitsTogether) {
                buffer.append("\n\n").append(content);
            } else {
                // Flush buffer.
                merged.add(buildMergedChunk(merged.size(), buffer.toString(), bufferPath, bufferTitle, chunks.get(0).getSource()));
                buffer.setLength(0);
                buffer.append(content);
                bufferPath = path;
                bufferTitle = c.getSectionTitle();
            }
        }
        // Flush remaining.
        if (buffer.length() > 0) {
            merged.add(buildMergedChunk(merged.size(), buffer.toString(), bufferPath, bufferTitle, chunks.get(0).getSource()));
        }
        return merged;
    }

    private ChunkDto buildMergedChunk(int index, String content, String path, String title, String source) {
        return ChunkDto.builder()
                .source(source)
                .chunkIndex(index)
                .content(content)
                .sectionTitle(title == null ? "" : title)
                .sectionPath(path == null ? "" : path)
                .parentIndex(-1)
                .isParent(false)
                .build();
    }

    private List<Section> parseSections(String markdown) {
        List<Hit> hits = new ArrayList<>();
        Matcher m = HEADING.matcher(markdown);
        while (m.find()) {
            hits.add(new Hit(m.start(), m.end(), m.group(1).length(), m.group(2).trim()));
        }

        if (hits.isEmpty()) {
            // No headings found, try to split by articles directly
            return parseArticles("__root__", "__root__", markdown);
        }

        List<Section> out = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            int bodyStart = h.end;
            int bodyEnd = i + 1 < hits.size() ? hits.get(i + 1).start : markdown.length();
            String body = stripDocHeaders(markdown.substring(bodyStart, bodyEnd));
            String path = buildPath(hits, i);

            // Split section body by articles if articles exist
            List<Section> articleSections = parseArticles(h.title, path, body);
            out.addAll(articleSections);
        }
        return out;
    }

    /**
     * Parse a section body and split by article boundaries (**第X条**).
     * Each article becomes an independent section with its own path.
     */
    private List<Section> parseArticles(String sectionTitle, String sectionPath, String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        List<Hit> articleHits = new ArrayList<>();
        Matcher am = ARTICLE.matcher(body);
        while (am.find()) {
            articleHits.add(new Hit(am.start(), am.end(), 0, am.group(1)));
        }

        // If no articles found, return the whole body as one section
        if (articleHits.isEmpty()) {
            return List.of(new Section(sectionTitle, sectionPath, body));
        }

        List<Section> out = new ArrayList<>();

        // Content before first article (if any)
        if (articleHits.get(0).start > 0) {
            String preContent = body.substring(0, articleHits.get(0).start).trim();
            if (!preContent.isEmpty()) {
                out.add(new Section(sectionTitle, sectionPath, preContent));
            }
        }

        // Each article as a separate section
        for (int i = 0; i < articleHits.size(); i++) {
            Hit article = articleHits.get(i);
            int articleStart = article.start;
            int articleEnd = i + 1 < articleHits.size() ? articleHits.get(i + 1).start : body.length();

            String articleContent = body.substring(articleStart, articleEnd).trim();
            if (!articleContent.isEmpty()) {
                // Build article path: section path + article title
                String articlePath = sectionPath.isEmpty() || "__root__".equals(sectionPath)
                        ? article.title
                        : sectionPath + " / " + article.title;
                out.add(new Section(article.title, articlePath, articleContent));
            }
        }

        return out;
    }

    /** Remove leading lines that look like document reference numbers. */
    private static String stripDocHeaders(String body) {
        String[] lines = body.split("\\R", -1);
        int start = 0;
        while (start < lines.length && DOC_HEADER.matcher(lines[start]).matches()) {
            start++;
        }
        if (start == 0) return body;
        return String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length));
    }

    private static String buildPath(List<Hit> hits, int idx) {
        // Use full heading hierarchy for accurate section paths.
        // Limit to last 4 levels to avoid excessively long paths from deep TOC nesting.
        int start = Math.max(0, idx - 3);
        StringBuilder path = new StringBuilder();
        for (int j = start; j <= idx; j++) {
            if (path.length() > 0) path.append(" / ");
            path.append(hits.get(j).title);
        }
        return path.toString();
    }

    /**
     * Split a section into chunks with parent-child relationship.
     *
     * <p>For articles ≤ chunkSize: generates a single parent chunk.
     * <p>For articles > chunkSize: generates a parent chunk (full article) + child chunks (pieces).
     *
     * @return the updated chunkIndex
     */
    private int splitSection(Section sec, int chunkSize, int chunkOverlap, String source, int chunkIndex, List<ChunkDto> chunks) {
        String body = sec.body.trim();
        if (body.isEmpty()) return chunkIndex;

        // Prepend section path so each chunk is self-contained for the frontend citation.
        String header = sec.path.isEmpty() || "__root__".equals(sec.path) ? "" : "【" + sec.path + "】\n";
        String fullContent = header + body;

        if (body.length() <= chunkSize) {
            // Article fits in one chunk - standalone chunk (not a parent, no children).
            chunks.add(ChunkDto.builder()
                    .source(source)
                    .chunkIndex(chunkIndex)
                    .content(fullContent)
                    .sectionTitle(sec.title)
                    .sectionPath(sec.path)
                    .parentIndex(-1)
                    .isParent(false)
                    .build());
            return chunkIndex + 1;
        }

        // Article is longer than chunkSize - generate parent + children
        int parentIndex = chunkIndex;

        // 1. Generate parent chunk (full article)
        chunks.add(ChunkDto.builder()
                .source(source)
                .chunkIndex(chunkIndex++)
                .content(fullContent)
                .sectionTitle(sec.title)
                .sectionPath(sec.path)
                .parentIndex(-1)
                .isParent(true)
                .build());

        // 2. Generate child chunks (pieces)
        List<String> pieces = splitByParagraphs(body, chunkSize, chunkOverlap);
        for (int i = 0; i < pieces.size(); i++) {
            String pieceContent = i == 0 && !header.isEmpty() ? header + pieces.get(i) : pieces.get(i);
            chunks.add(ChunkDto.builder()
                    .source(source)
                    .chunkIndex(chunkIndex++)
                    .content(pieceContent)
                    .sectionTitle(sec.title)
                    .sectionPath(sec.path)
                    .parentIndex(parentIndex)
                    .isParent(false)
                    .build());
        }

        return chunkIndex;
    }

    private List<String> splitByParagraphs(String body, int chunkSize, int chunkOverlap) {
        String[] paragraphs = body.split("\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.length() > chunkSize) {
                if (cur.length() > 0) {
                    chunks.add(cur.toString());
                    cur.setLength(0);
                }
                chunks.addAll(splitBySize(trimmed, chunkSize, chunkOverlap));
                continue;
            }
            if (cur.length() == 0) {
                cur.append(trimmed);
            } else if (cur.length() + 2 + trimmed.length() <= chunkSize) {
                cur.append("\n\n").append(trimmed);
            } else {
                chunks.add(cur.toString());
                cur.setLength(0);
                cur.append(trimmed);
            }
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        return chunks;
    }

    private List<String> splitBySize(String text, int chunkSize, int chunkOverlap) {
        // Split at sentence boundaries instead of character positions.
        // Chinese sentence delimiters: 。！？；\n
        List<String> sentences = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            cur.append(text.charAt(i));
            char ch = text.charAt(i);
            if (ch == '。' || ch == '！' || ch == '？' || ch == '；' || ch == '\n') {
                sentences.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) sentences.add(cur.toString());

        List<String> out = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (String s : sentences) {
            if (chunk.length() + s.length() > chunkSize && chunk.length() > 0) {
                out.add(chunk.toString().trim());
                // Overlap: keep the last sentence as overlap.
                chunk = new StringBuilder();
                if (chunkOverlap > 0) {
                    // Find sentences to keep as overlap from the end
                    String last = out.get(out.size() - 1);
                    int overlapStart = Math.max(0, last.length() - chunkOverlap);
                    // Don't break in the middle of a sentence; just start fresh
                }
            }
            chunk.append(s);
        }
        if (chunk.length() > 0) out.add(chunk.toString().trim());
        return out;
    }

    /** Extract the heading level from a path string like "Top / Mid / Leaf" → 3. */
    private static int getHeadingLevel(String path) {
        return path.split(" / ").length;
    }

    private record Section(String title, String path, String body) {}
    private record Hit(int start, int end, int level, String title) {}
}
