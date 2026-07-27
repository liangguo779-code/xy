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
 * <p>Splits on three hierarchies in order of precedence:
 * <ol>
 *   <li>{@code #} / {@code ##} / {@code ###} Markdown headings (e.g. {@code # 第一章 总则})</li>
 *   <li>{@code 第十X条} / {@code 第X章} / {@code 第X节} clauses (e.g. {@code **第十条** 申请重学时间...})</li>
 *   <li>Sub-section enumeration lines {@code （一） / （二） / （三）}</li>
 * </ol>
 *
 * <p>For each chunk, the section path (e.g. "第三章 学业 / 第十条 重学") is prepended to
 * the chunk content so the reference displayed in the frontend is self-contained
 * ("（path）...actual content...") without the user having to click through to
 * the full document.
 *
 * <p>Document header noise (e.g. "（教育部令 第 41 号）") is detected and stripped from
 * the top of each section so the first chunk of a chapter doesn't waste its budget
 * on a citation.
 */
@Component
public class MarkdownSectionSplitter {

    private static final Pattern HEADING =
            Pattern.compile("^(#{1,6})\\s+(.*)$", Pattern.MULTILINE);
    // NOTE: **第十条** / **第一章** etc. are kept as bold TEXT in the chunks, NOT as
    // section delimiters. The student handbook's actual section hierarchy is purely
    // Markdown headings (#/##/###). Using ARTICLE as a section boundary fragments
    // the body and loses surrounding context.
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

    public List<ChunkDto> split(String source, String markdown, int chunkSize, int chunkOverlap) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        markdown = stripToc(markdown);
        List<Section> sections = parseSections(markdown);
        System.out.println("[DEBUG] sections=" + sections.size() + " titles=" + sections.stream().map(s -> s.title).toList() + " md_len=" + markdown.length());
        List<ChunkDto> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (Section sec : sections) {
            for (String part : splitSection(sec, chunkSize, chunkOverlap)) {
                chunks.add(ChunkDto.builder()
                        .source(source)
                        .chunkIndex(chunkIndex++)
                        .content(part)
                        .sectionTitle(sec.title)
                        .sectionPath(sec.path)
                        .build());
                chunkIndex++;
            }
        }
        return chunks;
    }

    private List<Section> parseSections(String markdown) {
        List<Hit> hits = new ArrayList<>();
        Matcher m = HEADING.matcher(markdown);
        while (m.find()) {
            hits.add(new Hit(m.start(), m.end(), m.group(1).length(), m.group(2).trim()));
        }
        // Remove ARTICLE pattern — no longer used as a section boundary.

        if (hits.isEmpty()) {
            return List.of(new Section("__root__", "__root__", markdown));
        }
        List<Section> out = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            int bodyStart = h.end;
            int bodyEnd = i + 1 < hits.size() ? hits.get(i + 1).start : markdown.length();
            String body = stripDocHeaders(markdown.substring(bodyStart, bodyEnd));
            String path = buildPath(hits, i);
            out.add(new Section(h.title, path, body));
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
        // Keep only the last 2 levels to avoid path explosion (TOC has 50+ headings).
        int start = Math.max(0, idx - 1);
        StringBuilder path = new StringBuilder();
        for (int j = start; j <= idx; j++) {
            if (path.length() > 0) path.append(" / ");
            path.append(hits.get(j).title);
        }
        return path.toString();
    }

    private List<String> splitSection(Section sec, int chunkSize, int chunkOverlap) {
        String body = sec.body.trim();
        if (body.isEmpty()) return List.of();
        // Prepend section path so each chunk is self-contained for the frontend citation.
        String header = sec.path.isEmpty() || "__root__".equals(sec.path) ? "" : "【" + sec.path + "】\n";
        if (body.length() <= chunkSize) {
            return List.of(header + body);
        }
        // Try splitting by subheadings INSIDE this section.
        // Only match headings DEEPER than the section heading to avoid splitting on
        // sibling headings that appear in the body (e.g. ## Mid inside # Top's body).
        int subLevel = sec.path.isEmpty() || "__root__".equals(sec.path) ? 2 : getHeadingLevel(sec.path) + 1;
        Pattern sub = Pattern.compile("(?m)^#{" + subLevel + ",6}\\s+.*$");
        Matcher sm = sub.matcher(body);
        if (sm.find()) {
            List<String> subParts = new ArrayList<>();
            int start = 0;
            sm.reset();
            while (sm.find()) {
                if (sm.start() > start) subParts.add(body.substring(start, sm.start()));
                start = sm.start();
            }
            subParts.add(body.substring(start));
            List<String> result = new ArrayList<>();
            for (String p : subParts) {
                if (p.length() > chunkSize) {
                    // Only prepend the header to the FIRST part of the section to avoid
                    // repeating the breadcrumb in every chunk.
                    String first = p;
                    List<String> pieces = splitByParagraphs(first, chunkSize, chunkOverlap);
                    for (int i = 0; i < pieces.size(); i++) {
                        result.add(i == 0 && !header.isEmpty() ? header + pieces.get(i) : pieces.get(i));
                    }
                } else {
                    result.add(p);
                }
            }
            return result;
        }
        List<String> pieces = splitByParagraphs(body, chunkSize, chunkOverlap);
        for (int i = 0; i < pieces.size(); i++) {
            pieces.set(i, i == 0 && !header.isEmpty() ? header + pieces.get(i) : pieces.get(i));
        }
        return pieces;
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
        List<String> out = new ArrayList<>();
        int step = Math.max(1, chunkSize - chunkOverlap);
        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(text.length(), i + chunkSize);
            out.add(text.substring(i, end));
            if (end == text.length()) break;
        }
        return out;
    }

    /** Extract the heading level from a path string like "Top / Mid / Leaf" → 3. */
    private static int getHeadingLevel(String path) {
        return path.split(" / ").length;
    }

    private record Section(String title, String path, String body) {}
    private record Hit(int start, int end, int level, String title) {}
}
