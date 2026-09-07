package com.campus.ai.rag;

import com.campus.ai.config.AiProperties;

/**
 * 置信度分级器：将重排序后的最高分映射为 high / medium / low 三档。
 * 分数越低表示匹配越好（cross-encoder 取负余弦，范围 [-1, 0]）。
 *
 * <p>阈值从 {@link AiProperties.Knowledge} 加载，可通过配置调整：
 * <ul>
 *   <li>{@code highConfidenceThreshold}（默认 -0.6）：分数 ≤ 此值 → "high"</li>
 *   <li>{@code mediumConfidenceThreshold}（默认 -0.3）：分数 ≤ 此值 → "medium"</li>
 *   <li>其他 → "low"</li>
 * </ul>
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>RagOrchestrator 无法区分检索质量，所有结果都走同一条 LLM 生成路径</li>
 *   <li>低置信度时无法跳过 LLM 直接返回片段摘要，可能产生大量幻觉回答</li>
 *   <li>中置信度时无法附加免责声明，用户可能误将低质量回答当作权威信息</li>
 * </ul>
 */
public final class ConfidenceGrader {

    public static final String HIGH = "high";
    public static final String MEDIUM = "medium";
    public static final String LOW = "low";

    /** "完全没有检索结果"的哨兵值 —— 始终归类为 low。 */
    public static final double NO_RESULT = 999d;

    private ConfidenceGrader() {
    }

    public static String grade(double topRerankScore, AiProperties props) {
        if (topRerankScore == NO_RESULT) return LOW;
        double highT = props.getKnowledge().getHighConfidenceThreshold();
        double midT = props.getKnowledge().getMediumConfidenceThreshold();
        if (topRerankScore <= highT) return HIGH;
        if (topRerankScore <= midT) return MEDIUM;
        return LOW;
    }
}
