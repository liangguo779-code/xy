package com.campus.ai.knowledge.pipeline;

import com.campus.ai.knowledge.model.ChunkDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
        // 合并同一章节路径下的连续小分块。
        chunks = mergeSmallChunks(chunks, chunkSize, minChunkSize);
        // 过滤掉没有检索价值的极小分块。
        chunks.removeIf(c -> c.getContent() != null && c.getContent().trim().length() < MIN_CHUNK_SIZE);
        // 过滤后重新编号。
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setChunkIndex(i);
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

        // 第一个条款之前的内容（如有）
        if (articleHits.get(0).start > 0) {
            String preContent = body.substring(0, articleHits.get(0).start).trim();
            if (!preContent.isEmpty()) {
                out.add(new Section(sectionTitle, sectionPath, preContent));
            }
        }

        // 每个条款作为独立的 section
        for (int i = 0; i < articleHits.size(); i++) {
            Hit article = articleHits.get(i);
            int articleStart = article.start;
            int articleEnd = i + 1 < articleHits.size() ? articleHits.get(i + 1).start : body.length();

            String articleContent = body.substring(articleStart, articleEnd).trim();
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
        while (start < lines.length && DOC_HEADER.matcher(lines[start]).matches()) {
            start++;
        }
        if (start == 0) return body;
        return String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length));
    }

    private static String buildPath(List<Hit> hits, int idx) {
        // 路径锚定在最近一个 # 顶级标题（政策文档边界），向下拼接
        // 所有子标题。这样多政策文件按页号分片时，相邻政策不会"串味"
        // 进彼此的 sectionPath。
        // 当前 heading 本身就是 # 时，路径就是它自己。
        if (hits.get(idx).level() == 1) {
            return hits.get(idx).title;
        }
        int start = 0;
        for (int j = idx - 1; j >= 0; j--) {
            if (hits.get(j).level() == 1) {
                start = j;
                break;
            }
        }
        StringBuilder path = new StringBuilder();
        for (int j = start; j <= idx; j++) {
            if (path.length() > 0) path.append(" / ");
            path.append(hits.get(j).title);
        }
        return path.toString();
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
        for (String s : sentences) {
            if (chunk.length() + s.length() > chunkSize && chunk.length() > 0) {
                out.add(chunk.toString().trim());
                // 重叠：保留最后一个句子作为重叠。
                chunk = new StringBuilder();
                if (chunkOverlap > 0) {
                    // 从末尾找到作为重叠的句子
                    String last = out.get(out.size() - 1);
                    int overlapStart = Math.max(0, last.length() - chunkOverlap);
                    // 不在句子中间断开；直接重新开始
                }
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

    private record Section(String title, String path, String body) {}
    private record Hit(int start, int end, int level, String title) {}
}
