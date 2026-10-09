package com.campus.ai.rag;

import com.campus.ai.config.AiProperties;

/**
 * 置信度分级器：将重排序后的最高分映射为 high / medium / low 三档。
 * 分数越低表示匹配越好（范围 [-1, 0]，由 {@code ScoringConfig} 出口归一化保证）。
 *
 * <p>极性说明：OnnxScoringModel 原始输出是 raw logits，<b>越高越相关</b>
 * （实测相关对 -0.63/-2.24、无关对 -2.78/-10.12），并非"负余弦越低越好"；
 * 该极性已在 {@code ScoringConfig.NormalizedScoringModel} 出口翻转并压到 [-1,0]，
 * 因此本类的"≤ 阈值 → 更高置信度"比较方向成立，阈值无需重标定。
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
    // 分档常量与 ChatResponse.confidence 字段取值一一对应，前端据此展示不同提示样式。

    /** "完全没有检索结果"的哨兵值 —— 始终归类为 low。 */
    public static final double NO_RESULT = 999d;

    private ConfidenceGrader() {
    }

    /** 按阈值把重排序最高分映射为 high / medium / low，分数越低（越负）表示匹配越好。 */
    public static String grade(double topRerankScore, AiProperties props) {
        return grade(topRerankScore, props, false);
    }

    /**
     * 分档入口（含降级标记）。
     *
     * @param rerankDegraded 重排序模型缺失（NoOp 恒等分）时为 true（P1-12）。
     *        恒等分既会把 top0 判 low（全量跳过 LLM），也会把靠后段误判 high，
     *        因此降级时强制 medium（走 LLM 但带免责声明）；无检索结果仍是 low。
     */
    public static String grade(double topRerankScore, AiProperties props, boolean rerankDegraded) {
        if (topRerankScore == NO_RESULT) return LOW;
        if (rerankDegraded) return MEDIUM;
        double highT = props.getKnowledge().getHighConfidenceThreshold();
        double midT = props.getKnowledge().getMediumConfidenceThreshold();
        if (topRerankScore <= highT) return HIGH;
        if (topRerankScore <= midT) return MEDIUM;
        return LOW;
    }
}
