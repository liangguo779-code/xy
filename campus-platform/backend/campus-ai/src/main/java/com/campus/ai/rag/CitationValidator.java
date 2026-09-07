package com.campus.ai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用校验器：验证 LLM 生成的回答中 {@code [来源N]} 引用是否合法。
 *
 * <p>两层反幻觉防御：
 * <ol>
 *   <li>剥离超出范围的引用（{@code < 1} 或 {@code > validSourceCount}），
 *       保留周围文本使回答仍然通顺。</li>
 *   <li>当伪造引用过多时，调用方应回退到"低置信度"摘要路径，不再信任清理后的文本。</li>
 * </ol>
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>LLM 编造的虚假引用（如 [来源99]）会直接展示给用户，误导性极强</li>
 *   <li>无法检测"系统性幻觉"（大量引用都是假的），调用方无法触发兜底策略</li>
 *   <li>用户会把带引用的回答当作有依据的信息，实际上引用可能完全不存在</li>
 * </ul>
 */
public final class CitationValidator {

    /**
     * 匹配 {@code [来源N]}，允许可选的空白和前导负号。
     * 捕获组 1 为数字索引（可能为负数）。
     */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[来源\\s*(-?\\d+)\\s*\\]");

    /** 剥离比例超过此值时触发拒绝（即系统性幻觉）。 */
    private static final double REJECT_STRIP_RATIO = 0.5d;

    private CitationValidator() {
    }

    /**
     * @param answer          LLM 生成的回答文本
     * @param validSourceCount 提供给 LLM 的来源数量（即 {@code sources.size()}）
     * @return 校验结果：清理后的回答 + 日志/监控用的统计数据
     */
    public static Result validate(String answer, int validSourceCount) {
        if (answer == null || answer.isEmpty()) {
            return new Result(answer == null ? "" : answer, 0, 0, List.of());
        }
        if (validSourceCount <= 0) {
            // 没有可引用的来源 —— 剥离所有引用。返回完整回答仍然是安全的，
            // 因为调度器会通过 shouldReject 判断是否需要兜底。
            return stripAll(answer);
        }

        Matcher m = CITATION_PATTERN.matcher(answer);
        StringBuilder sb = new StringBuilder(answer.length());
        int total = 0;
        int stripped = 0;
        List<Integer> fabricated = new ArrayList<>();
        int lastEnd = 0;
        while (m.find()) {
            int n;
            try {
                n = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                // 捕获组 1 始终是 \d+，所以理论上不会走到这里；防御性处理。
                n = -1;
            }
            total++;
            if (n < 1 || n > validSourceCount) {
                stripped++;
                fabricated.add(n);
                // 只删除方括号 —— 保留周围文本完整。
                sb.append(answer, lastEnd, m.start());
                lastEnd = m.end();
            }
        }
        sb.append(answer, lastEnd, answer.length());
        return new Result(sb.toString(), total, total - stripped, fabricated);
    }

    /**
     * 判断已校验的回答是否应被拒绝为系统性幻觉。
     * 触发条件：
     * <ul>
     *   <li>LLM 完全没有引用来源（可能整段回答都是幻觉），或</li>
     *   <li>超过 {@link #REJECT_STRIP_RATIO} 的引用是伪造的。</li>
     * </ul>
     * 使用 {@link Result#totalCitations()} 和 {@link Result#fabricatedCount()} 的总数判断。
     */
    public static boolean shouldReject(int totalCitations, int fabricatedCount) {
        if (totalCitations == 0) return true;
        return (double) fabricatedCount / totalCitations > REJECT_STRIP_RATIO;
    }

    private static Result stripAll(String answer) {
        Matcher m = CITATION_PATTERN.matcher(answer);
        int total = 0;
        List<Integer> fabricated = new ArrayList<>();
        StringBuilder sb = new StringBuilder(answer.length());
        int lastEnd = 0;
        while (m.find()) {
            total++;
            fabricated.add(-1);
            sb.append(answer, lastEnd, m.start());
            lastEnd = m.end();
        }
        sb.append(answer, lastEnd, answer.length());
        return new Result(sb.toString(), total, 0, fabricated);
    }

    /**
     * @param cleanedAnswer       剥离超范围引用后的文本
     * @param totalCitations      观察到的 {@code [来源N]} 总出现次数
     * @param validCitations      通过校验的引用数
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
}
