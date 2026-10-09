package com.campus.ai.knowledge.pipeline;

import com.campus.ai.knowledge.model.ChunkDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 针对中文政策文档（如《学生手册》）优化的 Markdown 分块器。
 *
 * <p>按两个层次切分：
 * <ol>
 *   <li>{@code #} / {@code ##} / {@code ###} Markdown 标题（如 {@code # 第一章 总则}）</li>
 *   <li>{@code **第X条**} 条款（如 {@code **第十条** 申请重学时间...}）</li>
 * </ol>
 *
 * <p>每个条款被视为独立的分块边界。这为 RAG 查询（如"转专业条件"→ 直接匹配第二十一条）提供精确检索。
 *
 * <p>父子分块策略：
 * <ul>
 *   <li>小分块（≤chunkSize）：用于向量检索，提升召回率</li>
 *   <li>大分块（完整文章）：用于生成回答，提供完整上下文</li>
 * </ul>
 *
 * <p>每个分块的内容前面会拼接章节路径（如"第三章 学籍管理 / 第三节 转专业与转学 / 第二十一条"），
 * 使前端展示的引用来源自包含。
 *
 * <p>文档头部噪声（如"（教育部令 第 41 号）"）会被检测并从每章开头剥离，
 * 避免第一个分块的预算浪费在引用信息上。
 */
@Component
public class MarkdownSectionSplitter {

    private static final Pattern HEADING =
            Pattern.compile("^(#{1,6})\\s+(.*)$", Pattern.MULTILINE);
    // 条款模式：**第X条** 或 **第XX条** 或 **第XXX条**
    // 这是政策文档的主要分块边界。
    private static final Pattern ARTICLE =
            Pattern.compile("^\\*\\*(第[一二三四五六七八九十百千\\d]+条)\\*\\*", Pattern.MULTILINE);
    // 文档头部行（如"（教育部令 第 41 号）"），会浪费分块预算
    private static final Pattern DOC_HEADER =
            Pattern.compile("^\\s*[（(][^）)]*[）)]\\s*$", Pattern.MULTILINE);
    // 目录标题标记（中英文）。
    // 中文字符使用 Unicode 转义以避免编码损坏。
    private static final String[] TOC_MARKERS = {"目录", "Contents", "Table of Contents", "TOC"};

    /**
     * 在分块前从 Markdown 中剥离目录部分。
     * 目录部分包含子标题后跟项目列表，项目符号与下一个标题之间没有段落文本。
     * 通过检查节 body 是否有 3+ 行项目符号且无非项目符号非标题文本来检测此模式。
     * 避免使用 CJK 字符串字面量以防止写入工具编码损坏。
     */
    private static String stripToc(String markdown) {
        String[] lines = markdown.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith("#")) continue;
            int level = countHashes(line);
            // 找到当前 section 的结束位置
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

    /** 小于该字符数的分块无检索价值，直接丢弃。 */
    private static final int MIN_CHUNK_SIZE = 30;

    /** 便捷重载：小分块合并阈值默认 200 字符。 */
    public List<ChunkDto> split(String source, String markdown, int chunkSize, int chunkOverlap) {
        return split(source, markdown, chunkSize, chunkOverlap, 200);
    }

    /**
     * 主分块流程：剥目录 → 按标题/条款切分 → 父子分块 → 合并小分块 → 过滤过小块 → 重新编号。
     *
     * @param minChunkSize 小分块合并阈值（字符数），低于此值的相邻同章节分块会被合并
     */
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
        // 合并同一章节路径下的连续小分块。
        chunks = mergeSmallChunks(chunks, chunkSize, minChunkSize);
        // 过滤掉没有检索价值的极小分块。
        chunks.removeIf(c -> c.getContent() != null && c.getContent().trim().length() < MIN_CHUNK_SIZE);
        // 过滤后重新编号，并把 parentIndex 从旧编号空间映射到新编号空间。
        // 注意：parentIndex 引用的父分块（isParent）在 merge 阶段原样保留旧 chunkIndex，
        // 而子/合并分块的 chunkIndex 已被 merge 重写——所以映射只能以父分块的旧编号为 key。
        Map<Integer, Integer> parentRemap = new HashMap<>();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDto c = chunks.get(i);
            if (c.isParent()) {
                parentRemap.put(c.getChunkIndex(), i);
            }
        }
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDto c = chunks.get(i);
            if (c.getParentIndex() >= 0) {
                c.setParentIndex(parentRemap.getOrDefault(c.getParentIndex(), -1));
            }
            c.setChunkIndex(i);
        }
        return chunks;
    }

    /**
     * 合并同一章节路径下连续的小分块（低于 minChunkSize）。
     * 产生更少但更有意义的检索分块。
     *
     * <p>保留父分块引用：当缓冲区中所有待合并的子分块都来自同一个父分块
     * （{@code parentIndex} 相同且非 -1）时，合并结果保留该父分块引用；
     * 一旦混入单条款（{@code parentIndex == -1}）或来自不同父分块的子条款，
     * 父分块引用被置为 -1（合并后内容已无法唯一对应单个父分块）。
     */
    private List<ChunkDto> mergeSmallChunks(List<ChunkDto> chunks, int chunkSize, int minChunkSize) {
        if (chunks.isEmpty()) return chunks;
        List<ChunkDto> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String bufferPath = null;
        String bufferTitle = null;
        int bufferParentIndex = -1;

        for (ChunkDto c : chunks) {
            String content = c.getContent() == null ? "" : c.getContent().trim();
            if (content.isEmpty()) continue;

            // 如果是父分块（完整文章），保持原样。
            if (c.isParent()) {
                // 先刷新缓冲区。
                if (buffer.length() > 0) {
                    merged.add(buildMergedChunk(merged.size(), buffer.toString(), bufferPath, bufferTitle, chunks.get(0).getSource(), bufferParentIndex));
                    buffer.setLength(0);
                }
                merged.add(c);
                bufferPath = null;
                bufferTitle = null;
                bufferParentIndex = -1;
                continue;
            }

            String path = c.getSectionPath() == null ? "" : c.getSectionPath();
            int parentIdx = c.getParentIndex();

            // 缓冲区为空，开始累积。
            if (buffer.length() == 0) {
                buffer.append(content);
                bufferPath = path;
                bufferTitle = c.getSectionTitle();
                bufferParentIndex = parentIdx;
                continue;
            }

            // 同一章节路径且合并后大小 ≤ chunkSize：合并。
            boolean samePath = path.equals(bufferPath);
            boolean fitsTogether = buffer.length() + 2 + content.length() <= chunkSize;

            if (samePath && fitsTogether) {
                buffer.append("\n\n").append(content);
                // 父引用一致性：只有当所有被合并的子分块都来自同一父分块时才保留。
                if (parentIdx != bufferParentIndex) {
                    bufferParentIndex = -1;
                }
            } else {
                // 刷新缓冲区。
                merged.add(buildMergedChunk(merged.size(), buffer.toString(), bufferPath, bufferTitle, chunks.get(0).getSource(), bufferParentIndex));
                buffer.setLength(0);
                buffer.append(content);
                bufferPath = path;
                bufferTitle = c.getSectionTitle();
                bufferParentIndex = parentIdx;
            }
        }
        // 刷新剩余内容。
        if (buffer.length() > 0) {
            merged.add(buildMergedChunk(merged.size(), buffer.toString(), bufferPath, bufferTitle, chunks.get(0).getSource(), bufferParentIndex));
        }
        return merged;
    }

    /** 把合并缓冲区的内容组装为普通子分块（isParent=false）。 */
    private ChunkDto buildMergedChunk(int index, String content, String path, String title, String source, int parentIndex) {
        return ChunkDto.builder()
                .source(source)
                .chunkIndex(index)
                .content(content)
                .sectionTitle(title == null ? "" : title)
                .sectionPath(path == null ? "" : path)
                .parentIndex(parentIndex)
                .isParent(false)
                .build();
    }

    /** 按 Markdown 标题切出各 section（含层级路径），section 内再按条款细分；无标题时整体按条款切。 */
    private List<Section> parseSections(String markdown) {
        List<Hit> hits = new ArrayList<>();
        Matcher m = HEADING.matcher(markdown);
        while (m.find()) {
            hits.add(new Hit(m.start(), m.end(), m.group(1).length(), m.group(2).trim()));
        }

        if (hits.isEmpty()) {
            // 未找到标题，尝试直接按条款切分
            return parseArticles("__root__", "__root__", markdown);
        }

        List<Section> out = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            int bodyStart = h.end;
            int bodyEnd = i + 1 < hits.size() ? hits.get(i + 1).start : markdown.length();
            String body = stripDocHeaders(markdown.substring(bodyStart, bodyEnd));
            String path = buildPath(hits, i);

            // 如果存在条款，按条款切分 section body
            List<Section> articleSections = parseArticles(h.title, path, body);
            out.addAll(articleSections);
        }
        return mergeOrphanSources(out);
    }

    /**
     * 来源标注行若独占一个 section（如 ## 节标题与 ### 子标题之间、或节内无条款时），
     * 并入其后一节的正文开头。否则会形成"只有一行来源标注"的垃圾分块，
     * 且与后一节路径不同、无法被 mergeSmallChunks 合并。
     */
    private static List<Section> mergeOrphanSources(List<Section> sections) {
        List<Section> out = new ArrayList<>();
        String pending = null;
        for (Section sec : sections) {
            String body = sec.body() == null ? "" : sec.body().trim();
            if (body.matches("\\*\\*来源(补充)?[：:][^\\n]*\\*\\*")) {
                pending = pending == null ? body : pending + "\n\n" + body;
                continue;
            }
            if (pending != null) {
                body = pending + "\n\n" + body;
                pending = null;
            }
            out.add(new Section(sec.title(), sec.path(), body));
        }
        if (pending != null) {
            if (out.isEmpty()) {
                out.add(new Section("__root__", "__root__", pending));
            } else {
                Section last = out.remove(out.size() - 1);
                out.add(new Section(last.title(), last.path(), last.body() + "\n\n" + pending));
            }
        }
        return out;
    }

    /**
     * 解析 section body 并按条款边界（**第X条**）切分。
     * 每个条款成为独立的 section，拥有自己的路径。
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

        // 未找到条款，将整个 body 作为一个 section 返回
        if (articleHits.isEmpty()) {
            return List.of(new Section(sectionTitle, sectionPath, body));
        }

        List<Section> out = new ArrayList<>();

        // 第一个条款之前的内容（来源标注、短序言、小节标题等）并入第一个条款。
        // 若独立成块会形成"只有一行来源标注"的垃圾分块，且与首条款路径不同、
        // 无法被 mergeSmallChunks 合并——所以从源头并入，保证分块自包含。
        String preContent = articleHits.get(0).start > 0
                ? body.substring(0, articleHits.get(0).start).trim() : "";

        // 每个条款作为独立的 section
        for (int i = 0; i < articleHits.size(); i++) {
            Hit article = articleHits.get(i);
            int articleStart = article.start;
            int articleEnd = i + 1 < articleHits.size() ? articleHits.get(i + 1).start : body.length();

            String articleContent = body.substring(articleStart, articleEnd).trim();
            if (i == 0 && !preContent.isEmpty()) {
                articleContent = preContent + "\n\n" + articleContent;
            }
            if (!articleContent.isEmpty()) {
                // 构建条款路径：章节路径 + 条款标题
                String articlePath = sectionPath.isEmpty() || "__root__".equals(sectionPath)
                        ? article.title
                        : sectionPath + " / " + article.title;
                out.add(new Section(article.title, articlePath, articleContent));
            }
        }

        return out;
    }

    /** 移除开头看起来像文档引用编号的行。 */
    private static String stripDocHeaders(String body) {
        String[] lines = body.split("\\R", -1);
        int start = 0;
        // body 常以换行开头（标题匹配 end 不含换行），先跳过前导空行，
        // 否则第一行是空串导致文号行匹配失败、留下垃圾小块。
        while (start < lines.length && lines[start].trim().isEmpty()) {
            start++;
        }
        while (start < lines.length && DOC_HEADER.matcher(lines[start]).matches()) {
            start++;
            while (start < lines.length && lines[start].trim().isEmpty()) {
                start++;
            }
        }
        if (start == 0) return body;
        return String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length));
    }

    private static String buildPath(List<Hit> hits, int idx) {
        // 祖先链路径：从当前标题向上回溯，只收录层级严格递减的祖先标题。
        // 兄弟章节（如"第一章"与"第二章"）不再互相串进对方路径；
        // 多政策文件天然隔离——每个 # 顶级标题都是新的路径根。
        List<String> chain = new ArrayList<>();
        chain.add(hits.get(idx).title);
        int curLevel = hits.get(idx).level();
        for (int j = idx - 1; j >= 0; j--) {
            if (hits.get(j).level() < curLevel) {
                chain.add(0, hits.get(j).title);
                curLevel = hits.get(j).level();
                if (curLevel == 1) break;
            }
        }
        return String.join(" / ", chain);
    }

    /**
     * 将 section 切分为具有父子关系的分块。
     *
     * <p>条款 ≤ chunkSize：生成单个父分块。
     * <p>条款 > chunkSize：生成父分块（完整文章）+ 子分块（片段）。
     *
     * @return 更新后的 chunkIndex
     */
    private int splitSection(Section sec, int chunkSize, int chunkOverlap, String source, int chunkIndex, List<ChunkDto> chunks) {
        String body = sec.body.trim();
        if (body.isEmpty()) return chunkIndex;

        // 前置章节路径，使每个分块对前端引用来说是自包含的。
        String header = sec.path.isEmpty() || "__root__".equals(sec.path) ? "" : "【" + sec.path + "】\n";
        String fullContent = header + body;

        if (body.length() <= chunkSize) {
            // 条款可放入单个分块 —— 独立分块（非父分块，无子分块）。
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

        // 条款长于 chunkSize —— 生成父分块 + 子分块
        int parentIndex = chunkIndex;

        // 1. 生成父分块（完整文章）
        chunks.add(ChunkDto.builder()
                .source(source)
                .chunkIndex(chunkIndex++)
                .content(fullContent)
                .sectionTitle(sec.title)
                .sectionPath(sec.path)
                .parentIndex(-1)
                .isParent(true)
                .build());

        // 2. 生成子分块（片段）
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

    /** 优先按段落边界贪心装箱到 chunkSize；超长段落交给 splitBySize 按句切。 */
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

    /** 超长文本兜底切法：先按中文句子边界切句，再按 chunkSize 装箱，避免句子被拦腰截断。 */
    private List<String> splitBySize(String text, int chunkSize, int chunkOverlap) {
        // 按句子边界切分而非字符位置。
        // 中文句子分隔符：。！？；\n
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
        // 当前块开头的重叠种子长度：种子本身不算新内容，避免"只有种子"的碎块被单独刷出。
        int seedLen = 0;
        for (String s : sentences) {
            boolean onlySeed = chunk.length() <= seedLen;
            if (chunk.length() + s.length() > chunkSize && chunk.length() > 0 && !onlySeed) {
                String emitted = chunk.toString().trim();
                out.add(emitted);
                chunk.setLength(0);
                // 重叠：把上一块尾部 chunkOverlap 个字符并入下一块开头，缓解跨块语义断裂。
                // 只作用于超长块的 splitBySize 路径（单个段落内部），
                // **第X条** 条款 / 标题是硬边界，不会被强行重叠。
                // 重叠按字符对齐（不强行对齐句子边界），行为可预期。
                if (chunkOverlap > 0 && !emitted.isEmpty()) {
                    int overlapLen = Math.min(chunkOverlap, emitted.length());
                    // 重叠长度不超过 chunkSize - 1，保证新块仍能容纳新内容。
                    overlapLen = Math.min(overlapLen, Math.max(0, chunkSize - 1));
                    chunk.append(emitted, emitted.length() - overlapLen, emitted.length());
                }
                seedLen = chunk.length();
            }
            chunk.append(s);
        }
        if (chunk.length() > 0) out.add(chunk.toString().trim());
        return out;
    }

    /** 从路径字符串（如"Top / Mid / Leaf"）提取标题层级 → 3。 */
    private static int getHeadingLevel(String path) {
        return path.split(" / ").length;
    }

    /** 解析出的一个逻辑节：标题 + 层级路径 + 正文。 */
    private record Section(String title, String path, String body) {}
    /** 标题/条款在原文中的位置命中（字符偏移 + 层级 + 标题文本）。 */
    private record Hit(int start, int end, int level, String title) {}
}
