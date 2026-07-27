package com.campus.ai.knowledge.pipeline;

import com.campus.ai.knowledge.model.ChunkDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown-aware chunker that preserves section headings. Mirrors
 * {@code rag.splitter.split_markdown} from the previous Python service:
 * split on {@code #}/{@code ##}/{@code ###} headings, then sub-split any section larger
 * than the configured chunk size by subheadings, paragraphs, or character windowing.
 *
 * <p>Output metadata (source, chunk_index, section_title, section_path) is preserved so the
 * frontend citation list and the persisted BM25/vector metadata stay compatible.
 */
@Component
public class MarkdownSectionSplitter {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$", Pattern.MULTILINE);

    public List<ChunkDto> split(String source, String markdown, int chunkSize, int chunkOverlap) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<Section> sections = parseSections(markdown);
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
            }
        }
        return chunks;
    }

    private List<Section> parseSections(String markdown) {
        Matcher m = HEADING.matcher(markdown);
        List<Hit> hits = new ArrayList<>();
        while (m.find()) {
            hits.add(new Hit(m.start(), m.end(), m.group(1).length(), m.group(2).trim()));
        }
        if (hits.isEmpty()) {
            return List.of(new Section("__root__", "__root__", markdown));
        }
        List<Section> out = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            int bodyStart = h.end;
            int bodyEnd = i + 1 < hits.size() ? hits.get(i + 1).start : markdown.length();
            String body = markdown.substring(bodyStart, bodyEnd);
            String path = buildPath(hits, i);
            out.add(new Section(h.title, path, body));
        }
        return out;
    }

    private static String buildPath(List<Hit> hits, int idx) {
        StringBuilder path = new StringBuilder();
        // Walk from the start of the file to the i-th heading — every preceding heading
        // is a parent in the section's breadcrumb.
        for (int j = 0; j <= idx; j++) {
            if (j > 0) path.append(" / ");
            path.append(hits.get(j).title);
        }
        return path.toString();
    }

    private record Hit(int start, int end, int level, String title) {}

    private List<String> splitSection(Section sec, int chunkSize, int chunkOverlap) {
        String body = sec.body.trim();
        if (body.length() <= chunkSize) {
            return List.of(body);
        }
        // Try splitting by subheadings (## or deeper) inside this section.
        Pattern sub = Pattern.compile("(?m)^#{2,6}\\s+.*$");
        Matcher sm = sub.matcher(body);
        if (sm.find()) {
            List<String> subParts = new ArrayList<>();
            int start = 0;
            sm.reset();
            while (sm.find()) {
                if (sm.start() > start) {
                    subParts.add(body.substring(start, sm.start()));
                }
                start = sm.start();
            }
            subParts.add(body.substring(start));
            List<String> result = new ArrayList<>();
            for (String p : subParts) {
                if (p.length() > chunkSize) {
                    result.addAll(splitByParagraphs(p, chunkSize, chunkOverlap));
                } else {
                    result.add(p);
                }
            }
            return result;
        }
        return splitByParagraphs(body, chunkSize, chunkOverlap);
    }

    private List<String> splitByParagraphs(String body, int chunkSize, int chunkOverlap) {
        // Split on blank lines; merge small paragraphs into a single chunk up to chunkSize.
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

    private record Section(String title, String path, String body) {}
}
