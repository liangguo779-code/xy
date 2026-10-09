package com.campus.ai.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用校验器：验证 LLM 生成的回答中 {@code [来源N]} 引用是否合法。
 *
 * <p>三层反幻觉防御：
 * <ol>
 *   <li>剥离超出范围的引用（{@code < 1} 或 {@code > validSourceCount}），
 *       保留周围文本使回答仍然通顺。</li>
 *   <li>内容级校验：引用句与 {@code sources[N-1].content} 做字面重合率比对，
 *       "编号合法但张冠李戴"的引用同样计入伪造并剥离。</li>
 *   <li>当伪造引用过多时，调用方应回退到"低置信度"摘要路径，不再信任清理后的文本。
 *       零引用不再一律拒绝（由调用方降级 + 免责声明）。</li>
 * </ol>
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>LLM 编造的虚假引用（如 [来源99]）会直接展示给用户，误导性极强</li>
 *   <li>无法检测"系统性幻觉"（大量引用都是假的），调用方无法触发兜底策略</li>
 *   <li>无法检测"张冠李戴"（引用了真实存在但内容不符的来源）</li>
 *   <li>用户会把带引用的回答当作有依据的信息，实际上引用可能完全不存在</li>
 * </ul>
 */
public final class CitationValidator {

    /**
     * 匹配引用标记的常见变体（P2-7 + 格式兼容）：
     * <ul>
     *   <li>{@code [来源N]} / {@code [来源Ｎ]}（全角数字），N 可多位、可带负号、允许内部空白；</li>
     *   <li>{@code [N]} 纯数字变体，仅允许 1-2 位 —— 避免把 {@code [2024]} 之类年份误判为引用；</li>
     *   <li>{@code 【来源N】} 六角括号、{@code （来源N）}/{@code (来源N)} 圆括号 —— 中文模型高频输出变体。
     *       这两类只支持"来源"前缀形式：{@code (24)} 这类纯数字圆括号多为注释序号，不按引用处理；
     *       {@code 【置信度提示】} 这类非引用六角括号也因"来源"后未跟数字而不匹配；</li>
     *   <li>{@code 来源N} 无括号裸形式 —— 仅当前后是标点/空白/句边界（非字母数字）时匹配，
     *       防止把正文叙述"来源1是错误的"误判为引用。</li>
     * </ul>
     * 全角/半角方括号均可。捕获组 1（方括号+来源前缀）/ 组 2（方括号纯数字）/
     * 组 3（六角或圆括号）/ 组 4（裸形式）为数字索引。
     */
    private static final Pattern CITATION_PATTERN = Pattern.compile(
            "[\\[［]\\s*(?:来源\\s*(-?[0-9０-９]{1,6})|(-?[0-9０-９]{1,2}))\\s*[\\]］]"
                    + "|[【（(]\\s*来源\\s*(-?[0-9０-９]{1,6})\\s*[】）)]"
                    + "|(?<![\\p{L}\\p{N}])来源\\s*(-?[0-9０-９]{1,6})(?![\\p{L}\\p{N}])");

    /** 剥离比例超过此值时触发拒绝（即系统性幻觉）。 */
    private static final double REJECT_STRIP_RATIO = 0.5d;

    /**
     * 引用句与被引来源的字面重合率阈值（P1-9 内容级校验）。
     * 取值偏低以避免误伤合理改写，只拦截"张冠李戴"式引用。
     */
    private static final double CONTENT_SIMILARITY_THRESHOLD = 0.2d;

    // ── 零引用自动补注（系统补注）参数 ─────────────────────────────────────────────

    /**
     * 自动补注的字面相似度阈值，刻意略低于 {@link #CONTENT_SIMILARITY_THRESHOLD}：
     * 补注是"从已有来源里为句子找出处"，宁多勿漏；反伪造是"验证模型声明"，宁可错杀。
     * 两把尺子职责不同，不应混用（详见 {@link #autoCite} 的复检说明）。
     */
    private static final double AUTO_CITE_MIN_SCORE = 0.15d;
    /** 自动补注：句子参与匹配的最小去噪长度（寒暄/纯标点句不参与，防止空串相似度 1.0 乱注）。 */
    private static final int AUTO_CITE_MIN_CHARS = 6;
    /** 自动补注：至少命中的 2-gram 数 —— 防单 gram 巧合（如仅共享"申请"二字）造成张冠李戴。 */
    private static final int AUTO_CITE_MIN_GRAM_HITS = 2;
    /** 自动补注：每句最多注入的来源数。 */
    private static final int AUTO_CITE_MAX_PER_SENTENCE = 2;
    /**
     * 自动补注：第 2 来源相对 top1 的最低得分比例。
     * 取 top1 即可满足标注需求；仅当第 2 名同样强（非同分且 ≥ top1 的 8 成）才并列注入，
     * 同分并列时只取编号最小者，避免把一批并列来源整段塞进句末。
     */
    private static final double AUTO_CITE_SECOND_RATIO = 0.8d;

    /**
     * 剥离标记后易残留的悬挂连接词（长词优先匹配）。
     * 例："具体规定详见[来源3]，也参考[来源4]"两处伪造时应清成"具体规定"，
     * 而不是留下"详见，也参考"这类语病（P2-8 轻量版）。
     */
    private static final String[] DANGLING_CONNECTORS = {
            "同时参考", "也参考", "并参考", "详见", "参见", "请参阅", "参考", "例如", "比如"};

    /** 句子边界字符：用于切分引用句以及判断悬挂链是否已到句末。 */
    private static final String SENTENCE_ENDS = "。！？；;\n";

    private CitationValidator() {
    }

    /**
     * 仅按编号范围校验（不比对内容）。
     *
     * @param answer          LLM 生成的回答文本
     * @param validSourceCount 提供给 LLM 的来源数量（即 {@code sources.size()}）
     * @return 校验结果：清理后的回答 + 日志/监控用的统计数据
     */
    public static Result validate(String answer, int validSourceCount) {
        return doValidate(answer, validSourceCount, null);
    }

    /**
     * 编号范围 + 内容级校验（P1-9）：引用句须与 {@code sourceContents.get(N-1)} 有足够字面重合，
     * 否则按伪造处理（剥离并计入伪造占比）。{@code sourceContents} 为空时退化为纯编号校验。
     *
     * @param answer         LLM 生成的回答文本
     * @param sourceContents 各来源正文，顺序与 {@code [来源N]} 编号一致（index = N-1）
     */
    public static Result validate(String answer, List<String> sourceContents) {
        return doValidate(answer, sourceContents == null ? 0 : sourceContents.size(), sourceContents);
    }

    /** 校验核心：收集伪造引用（越界 + 内容不符）及其剥离区间，合并后统一删除。 */
    private static Result doValidate(String answer, int validSourceCount, List<String> sourceContents) {
        if (answer == null || answer.isEmpty()) {
            return new Result(answer == null ? "" : answer, 0, 0, List.of());
        }
        if (validSourceCount <= 0) {
            // 没有可引用的来源 —— 剥离所有引用。返回完整回答仍然是安全的，
            // 因为调度器会通过 shouldReject 判断是否需要兜底。
            return stripAll(answer);
        }

        Matcher m = CITATION_PATTERN.matcher(answer);
        int total = 0;
        List<Integer> fabricated = new ArrayList<>();
        // 待剥离区间 [start,end)，已含悬挂连接词的扩展范围；重叠区间最后合并删除
        List<int[]> spans = new ArrayList<>();
        while (m.find()) {
            total++;
            int n = parseIndex(m);
            boolean bad;
            if (n < 1 || n > validSourceCount) {
                bad = true;
            } else if (sourceContents != null && !contentMatches(answer, m.start(), m.end(), sourceContents.get(n - 1))) {
                // P1-9：编号合法但句子内容与被引来源不符（张冠李戴），同样按伪造处理
                bad = true;
            } else {
                bad = false;
            }
            if (bad) {
                fabricated.add(n);
                spans.add(expandSpan(answer, m.start(), m.end()));
            }
        }
        return new Result(removeSpans(answer, spans), total, total - fabricated.size(), fabricated);
    }

    /**
     * 判断已校验的回答是否应被拒绝为系统性幻觉。
     * 仅当超过 {@link #REJECT_STRIP_RATIO} 的引用是伪造的才整体替换。
     * 零引用不再一律拒绝（P1-10）：规范的无引用短答案误伤代价大，
     * 改由调度器降级处理（追加免责声明 / 降 medium）。
     */
    public static boolean shouldReject(int totalCitations, int fabricatedCount) {
        if (totalCitations <= 0) return false;
        return (double) fabricatedCount / totalCitations > REJECT_STRIP_RATIO;
    }

    /** 无有效来源时剥离全部引用标记，fabricated 索引统一记为 -1 哨兵。 */
    private static Result stripAll(String answer) {
        Matcher m = CITATION_PATTERN.matcher(answer);
        int total = 0;
        List<Integer> fabricated = new ArrayList<>();
        List<int[]> spans = new ArrayList<>();
        while (m.find()) {
            total++;
            fabricated.add(-1);
            spans.add(expandSpan(answer, m.start(), m.end()));
        }
        return new Result(removeSpans(answer, spans), total, 0, fabricated);
    }

    /** 从匹配中取出数字索引（4 个捕获组取首个非空）：兼容全角数字与全角负号。 */
    private static int parseIndex(Matcher m) {
        String raw = null;
        for (int g = 1; g <= 4; g++) {
            if (m.group(g) != null) {
                raw = m.group(g);
                break;
            }
        }
        if (raw == null) return -1;
        try {
            return Integer.parseInt(normalizeDigits(raw));
        } catch (NumberFormatException e) {
            // 捕获组始终是数字字面量，理论上不会走到这里；防御性处理。
            return -1;
        }
    }

    /** 全角数字/全角负号转半角，供 Integer.parseInt 解析。 */
    private static String normalizeDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') sb.append((char) ('0' + (c - '０')));
            else if (c == '－' || c == '−') sb.append('-');
            else sb.append(c);
        }
        return sb.toString();
    }

    // ── 内容级校验（P1-9） ────────────────────────────────────────────────────────

    /** 引用句与被引来源内容是否相符（字面重合率 ≥ 阈值）。 */
    private static boolean contentMatches(String answer, int start, int end, String sourceContent) {
        String sentence = extractSentence(answer, start, end);
        return literalSimilarity(sentence, sourceContent) >= CONTENT_SIMILARITY_THRESHOLD;
    }

    /** 取引用标记所在句（以句末标点/换行为界），用于与被引来源比对。 */
    private static String extractSentence(String answer, int start, int end) {
        int s = start;
        while (s > 0 && SENTENCE_ENDS.indexOf(answer.charAt(s - 1)) < 0) s--;
        int e = end;
        while (e < answer.length() && SENTENCE_ENDS.indexOf(answer.charAt(e)) < 0) e++;
        return answer.substring(s, e);
    }

    /**
     * 字面重合率：引用句的字符 2-gram 出现在来源文本中的比例（去掉标点/空白/引用标记后计算）。
     * 不用 embedding 是为了零外部依赖、可离线单测；2-gram 比单字更能区分"张冠李戴"。
     */
    static double literalSimilarity(String sentence, String sourceContent) {
        int[] stat = gramStat(sentence, sourceContent);
        if (stat[1] == 0) return 1.0d;   // 句中只剩引用标记，无从比对，放过
        return (double) stat[0] / stat[1];
    }

    /**
     * 2-gram 命中统计：{@code [命中数, 总 2-gram 数]}，供 {@link #literalSimilarity}
     * 与自动补注（{@link #autoCite}）共用同一套去噪与切 gram 逻辑。
     * 单字句退化为子串包含判断；空串返回 {@code [0,0]}（由调用方决定语义）。
     */
    private static int[] gramStat(String sentence, String sourceContent) {
        String a = stripNoise(sentence);
        String b = stripNoise(sourceContent);
        if (a.isEmpty()) return new int[]{0, 0};
        if (a.length() == 1) return new int[]{b.indexOf(a) >= 0 ? 1 : 0, 1};
        Set<String> grams = new LinkedHashSet<>();
        for (int i = 0; i < a.length() - 1; i++) grams.add(a.substring(i, i + 2));
        int hit = 0;
        for (String g : grams) if (b.contains(g)) hit++;
        return new int[]{hit, grams.size()};
    }

    /** 去掉引用标记与非字母数字字符，只留中英文与数字，供字面比对。 */
    private static String stripNoise(String s) {
        String noCite = CITATION_PATTERN.matcher(s).replaceAll("");
        StringBuilder sb = new StringBuilder(noCite.length());
        for (int i = 0; i < noCite.length(); i++) {
            char c = noCite.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    // ── 零引用自动补注（系统补注） ───────────────────────────────────────────────

    /**
     * 零引用自动补注：按句切分回答（复用 {@link #SENTENCE_ENDS}），对每个含实质内容的句子
     * 与各来源正文做字面相似度匹配，命中则在句末注入 {@code [来源N]}（格式与上下文标注一致）。
     *
     * <p>注入的标记是"系统补注"而非模型原文：调用方不应按"模型伪造被修正"发 correction 事件；
     * 流式路径下若最终文本与模型原文不一致，由调用方的统一替换通道携带补注后全文即可。
     * 若后续需要在事件语义上区分"系统补注"与"模型伪造"，给 correction payload 加类型字段即可。
     *
     * <p>匹配策略与阈值取舍：
     * <ul>
     *   <li>仅处理去噪后 ≥ {@link #AUTO_CITE_MIN_CHARS} 字的实质句：寒暄/纯标点句不参与，
     *       否则 {@link #literalSimilarity} 对空串返回 1.0 会把编号乱注到问候语上；</li>
     *   <li>相似度阈值 {@link #AUTO_CITE_MIN_SCORE}（0.15）略低于反伪造的 0.2：补注宁多勿漏。
     *       因此注入后的复检只做编号解析/范围校验（{@code validate(answer, int)}），
     *       不再按 0.2 做内容剥离 —— 否则 0.15~0.2 区间的合法补注会被同一把尺子"回滚"掉，自我矛盾；</li>
     *   <li>防张冠李戴：至少 {@link #AUTO_CITE_MIN_GRAM_HITS} 个 2-gram 命中（防单词巧合），
     *       多个来源同分取编号最小者（"取最相似者"的确定性落实）；
     *       每句最多 {@link #AUTO_CITE_MAX_PER_SENTENCE} 个来源，第 2 名须与 top1 非同分且
     *       ≥ {@link #AUTO_CITE_SECOND_RATIO} × top1 才并列注入。</li>
     * </ul>
     *
     * @param answer          零引用的回答文本
     * @param sourceContents  各来源正文，顺序与 {@code [来源N]} 编号一致（index = N-1）
     */
    public static AutoCite autoCite(String answer, List<String> sourceContents) {
        if (answer == null || answer.isEmpty() || sourceContents == null || sourceContents.isEmpty()) {
            return new AutoCite(answer == null ? "" : answer, 0, 0);
        }
        StringBuilder out = new StringBuilder(answer.length() + 32);
        int injectedSentences = 0;
        int substantiveSentences = 0;
        int cursor = 0;
        for (int[] span : splitSentences(answer)) {
            String sentence = answer.substring(span[0], span[1]);
            // 句末注入位置：紧贴末尾句读之前，如"…申请[来源1]。"
            int insertAt = span[1];
            while (insertAt > span[0] && SENTENCE_ENDS.indexOf(answer.charAt(insertAt - 1)) >= 0) insertAt--;
            String marker = "";
            if (stripNoise(sentence).length() >= AUTO_CITE_MIN_CHARS) {
                substantiveSentences++;
                List<Integer> matched = matchSources(sentence, sourceContents);
                if (!matched.isEmpty()) {
                    injectedSentences++;
                    StringBuilder mk = new StringBuilder();
                    for (int n : matched) mk.append("[来源").append(n).append("]");
                    marker = mk.toString();
                }
            }
            out.append(answer, cursor, insertAt).append(marker);
            cursor = insertAt;
        }
        out.append(answer, cursor, answer.length());
        return new AutoCite(out.toString(), injectedSentences, substantiveSentences);
    }

    /** 按句边界切分（句末标点归入前句），返回各句在原文中的 {@code [start,end)} 区间，连续覆盖全文。 */
    private static List<int[]> splitSentences(String answer) {
        List<int[]> spans = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < answer.length(); i++) {
            if (SENTENCE_ENDS.indexOf(answer.charAt(i)) >= 0) {
                spans.add(new int[]{start, i + 1});
                start = i + 1;
            }
        }
        if (start < answer.length()) spans.add(new int[]{start, answer.length()});
        return spans;
    }

    /**
     * 找出与句子字面最相似的来源编号（1 基），按相似度降序、同分按编号升序，
     * 取 top1；第 2 名仅当非同分且得分 ≥ top1 的 {@link #AUTO_CITE_SECOND_RATIO} 时并列（上限 2 个）。
     * 候选须同时满足：相似度 ≥ {@link #AUTO_CITE_MIN_SCORE} 且 2-gram 命中数 ≥ {@link #AUTO_CITE_MIN_GRAM_HITS}。
     */
    private static List<Integer> matchSources(String sentence, List<String> sourceContents) {
        record Cand(int index, double score) {}
        List<Cand> cands = new ArrayList<>();
        for (int i = 0; i < sourceContents.size(); i++) {
            String source = sourceContents.get(i);
            if (source == null || source.isEmpty()) continue;
            int[] stat = gramStat(sentence, source);
            if (stat[1] == 0 || stat[0] < AUTO_CITE_MIN_GRAM_HITS) continue;
            double score = (double) stat[0] / stat[1];
            if (score >= AUTO_CITE_MIN_SCORE) cands.add(new Cand(i + 1, score));
        }
        cands.sort((a, b) -> a.score() == b.score()
                ? Integer.compare(a.index(), b.index())
                : Double.compare(b.score(), a.score()));
        List<Integer> result = new ArrayList<>();
        if (cands.isEmpty()) return result;
        result.add(cands.get(0).index());
        if (cands.size() >= 2 && AUTO_CITE_MAX_PER_SENTENCE >= 2
                && cands.get(1).score() < cands.get(0).score()
                && cands.get(1).score() >= cands.get(0).score() * AUTO_CITE_SECOND_RATIO) {
            result.add(cands.get(1).index());
        }
        return result;
    }

    // ── 剥离与残留语病清理（P2-8 轻量版） ─────────────────────────────────────────

    /**
     * 计算剥离区间：除标记本身外，向前吃掉紧邻的悬挂连接词（详见/也参考…）及其前导逗号，
     * 向后吃掉"，也参考"类悬挂链。
     * 向后扩展仅当链后是句末标点/结束/另一个引用标记时才生效，
     * 避免误删"参考下文"这类正常文本。
     */
    private static int[] expandSpan(String text, int start, int end) {
        int len = text.length();
        int s = start;
        while (s > 0 && Character.isWhitespace(text.charAt(s - 1))) s--;
        int connectorStart = matchConnectorBackward(text, s);
        if (connectorStart >= 0) {
            s = connectorStart;
            while (s > 0 && Character.isWhitespace(text.charAt(s - 1))) s--;
            // 连接词前的悬挂逗号一并清除，使相邻标记的扩展区间能合并
            if (s > 0 && "，,、".indexOf(text.charAt(s - 1)) >= 0) s--;
        }

        int e = end;
        int k = skipWhitespace(text, end, 1);
        int k2 = k;
        if (k2 < len && "，,、".indexOf(text.charAt(k2)) >= 0) k2++;
        k2 = skipWhitespace(text, k2, 1);
        int afterConnector = matchConnector(text, k2);
        if (afterConnector > k2) {
            k2 = skipWhitespace(text, afterConnector, 1);
            if (k2 < len && "，,、".indexOf(text.charAt(k2)) >= 0) k2++;
            if (k2 >= len || SENTENCE_ENDS.indexOf(text.charAt(k2)) >= 0 || isBracket(text.charAt(k2))) {
                e = k2;
            }
        }
        return new int[]{s, e};
    }

    /** 向前匹配悬挂连接词：返回连接词起始下标，无匹配返回 -1。 */
    private static int matchConnectorBackward(String text, int pos) {
        for (String c : DANGLING_CONNECTORS) {
            if (pos - c.length() >= 0 && text.startsWith(c, pos - c.length())) return pos - c.length();
        }
        return -1;
    }

    /** 向后匹配悬挂连接词：返回连接词之后的下标，无匹配返回 -1。 */
    private static int matchConnector(String text, int pos) {
        for (String c : DANGLING_CONNECTORS) {
            if (text.startsWith(c, pos)) return pos + c.length();
        }
        return -1;
    }

    private static int skipWhitespace(String text, int pos, int dir) {
        int p = pos;
        while (p >= 0 && p < text.length() && Character.isWhitespace(text.charAt(p))) p += dir;
        return p;
    }

    /** 引用标记的起始括号（全/半角方括号/六角括号/圆括号）——悬挂链的终止条件之一：后面紧跟另一个引用标记。 */
    private static boolean isBracket(char c) {
        return c == '[' || c == '［' || c == '【' || c == '（' || c == '(';
    }

    /** 合并重叠/相邻区间后按区间删除文本，再做残留语病清理。 */
    private static String removeSpans(String text, List<int[]> spans) {
        if (spans.isEmpty()) return text;
        spans.sort(Comparator.comparingInt(a -> a[0]));
        StringBuilder sb = new StringBuilder(text.length());
        int last = 0;
        for (int[] span : spans) {
            int s = Math.max(span[0], last);
            if (s > last) sb.append(text, last, s);
            last = Math.max(last, span[1]);
        }
        sb.append(text, last, text.length());
        return cleanupResidual(sb.toString());
    }

    /**
     * 剥离后的残留语病轻量清理：空括号、连续/悬挂逗号、仅剩标点的孤立回答。
     * 不做编号映射改写（P2-8 明确为轻量版）。
     */
    private static String cleanupResidual(String s) {
        String out = s.replaceAll("（\\s*）|\\(\\s*\\)|【\\s*】|\\[\\s*\\]|［\\s*］", "");
        out = out.replaceAll("，\\s*[，,、]+", "，");
        out = out.replaceAll("([。！？；;])\\s*[，,、]+", "$1");
        out = out.replaceAll("^[，,、]+\\s*", "");
        out = out.replaceAll("\\s*[，,、]+$", "");
        // 标记剥离后只剩标点/空白时返回空串，避免给用户看到孤立的"。"
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (!Character.isWhitespace(c) && "。！？；;，,、".indexOf(c) < 0) return out;
        }
        return "";
    }

    /**
     * @param cleanedAnswer       剥离伪造引用（含内容不符）后的文本
     * @param totalCitations      观察到的引用总出现次数
     * @param validCitations      通过校验的引用数（编号合法且内容相符）
     * @param fabricatedIndices   被剥离的数字索引（当整段回答没有有效来源时为负数哨兵 {@code -1}）
     */
    public record Result(
            String cleanedAnswer,
            int totalCitations,
            int validCitations,
            List<Integer> fabricatedIndices) {

        public int fabricatedCount() {
            return totalCitations - validCitations;
        }
    }

    /**
     * @param annotatedAnswer      系统补注后的回答文本（无命中时与原文相同）
     * @param injectedSentences    被注入引用的句子数（覆盖率分子）
     * @param substantiveSentences 含实质内容的句子数（覆盖率分母）
     */
    public record AutoCite(
            String annotatedAnswer,
            int injectedSentences,
            int substantiveSentences) {

        /** 补注覆盖率：被补注句子占实质内容句子的比例，取值 [0,1]。 */
        public double coverage() {
            return substantiveSentences == 0 ? 0.0d : (double) injectedSentences / substantiveSentences;
        }
    }
}
