package com.campus.ai.rag;

import com.campus.ai.config.AiProperties;
import com.campus.ai.config.ScoringConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConfidenceGrader 分档边界测试：阈值含等号归属、NO_RESULT 哨兵、自定义阈值、降级口径。
 *
 * <p>极性约定：本类消费的分数是 ScoringConfig 出口的归一化域 [-1,0]，越低越相关。
 * OnnxScoringModel 原始 raw logits 是越高越相关，见 {@link ScoringConfig#normalizeLogit}。
 */
class ConfidenceGraderTest {

    private AiProperties propsWith(double high, double mid) {
        AiProperties p = new AiProperties();
        p.getKnowledge().setHighConfidenceThreshold(high);
        p.getKnowledge().setMediumConfidenceThreshold(mid);
        return p;
    }

    @Test
    void highConfidenceAtUpperBoundary() {
        // Default thresholds: high=-0.6, medium=-0.3
        assertEquals(ConfidenceGrader.HIGH, ConfidenceGrader.grade(-0.6,
                propsWith(-0.6, -0.3)));
    }

    @Test
    void highConfidenceAboveUpperBoundary() {
        assertEquals(ConfidenceGrader.HIGH, ConfidenceGrader.grade(-0.7,
                propsWith(-0.6, -0.3)));
        assertEquals(ConfidenceGrader.HIGH, ConfidenceGrader.grade(-1.0,
                propsWith(-0.6, -0.3)));
    }

    @Test
    void mediumConfidenceBetweenBoundaries() {
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(-0.5,
                propsWith(-0.6, -0.3)));
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(-0.4,
                propsWith(-0.6, -0.3)));
    }

    @Test
    void mediumConfidenceAtLowerBoundary() {
        // Inclusive boundary: -0.3 still counts as medium
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(-0.3,
                propsWith(-0.6, -0.3)));
    }

    @Test
    void lowConfidenceAboveLowerBoundary() {
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(-0.29,
                propsWith(-0.6, -0.3)));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(-0.1,
                propsWith(-0.6, -0.3)));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(0.0,
                propsWith(-0.6, -0.3)));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(0.5,
                propsWith(-0.6, -0.3)));
    }

    @Test
    void noResultSentinelIsAlwaysLow() {
        assertEquals(ConfidenceGrader.LOW,
                ConfidenceGrader.grade(ConfidenceGrader.NO_RESULT, new AiProperties()));
    }

    @Test
    void customThresholdsAreRespected() {
        // Tight high bar: only very good matches count
        AiProperties p = propsWith(-0.8, -0.5);
        assertEquals(ConfidenceGrader.HIGH, ConfidenceGrader.grade(-0.9, p));
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(-0.6, p));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(-0.3, p));
    }

    @Test
    void degradedRerankerForcesMedium() {
        // P1-12：NoOp 恒等分不可信 —— 恒等分 0 会判 low（全量跳过 LLM）、-3 会误判 high，
        // 降级时强制 medium（走 LLM + 免责声明）
        AiProperties p = propsWith(-0.6, -0.3);
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(0.0, p, true));
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(-0.9, p, true));
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(-3.0, p, true));
    }

    @Test
    void degradedRerankerStillLowOnNoResult() {
        // 无检索结果仍是 low，兜底摘要语义不变
        assertEquals(ConfidenceGrader.LOW,
                ConfidenceGrader.grade(ConfidenceGrader.NO_RESULT, propsWith(-0.6, -0.3), true));
    }

    @Test
    void nonDegradedKeepsNormalGrading() {
        // 三参重载传 false 时与二参行为一致
        AiProperties p = propsWith(-0.6, -0.3);
        assertEquals(ConfidenceGrader.HIGH, ConfidenceGrader.grade(-0.7, p, false));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(0.0, p, false));
    }

    // ── 极性标定：raw logit（越高越相关）→ 归一化域 [-1,0]（越低越好）────────────

    @Test
    void rawLogitAnchorsLandInCorrectBands() {
        // 实测 bge-reranker-base raw logits：相关对 -0.63/-2.24，无关对 -2.78/-10.12。
        // 经 normalizeLogit 归一化后必须落入正确的置信度档 —— 若极性反转会全判 high
        AiProperties p = propsWith(-0.6, -0.3);
        assertEquals(ConfidenceGrader.HIGH, ConfidenceGrader.grade(ScoringConfig.normalizeLogit(-0.63), p));
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(ScoringConfig.normalizeLogit(-2.24), p));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(ScoringConfig.normalizeLogit(-2.78), p));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(ScoringConfig.normalizeLogit(-10.12), p));
    }

    @Test
    void normalizeLogitIsMonotonicWithModelPolarity() {
        // raw logits 越高越相关 ⇒ 归一化分数越低越好，序关系必须保持
        double relevant = ScoringConfig.normalizeLogit(-0.63);
        double weak = ScoringConfig.normalizeLogit(-2.24);
        double irrelevant = ScoringConfig.normalizeLogit(-2.78);
        double far = ScoringConfig.normalizeLogit(-10.12);
        assertTrue(relevant < weak);
        assertTrue(weak < irrelevant);
        assertTrue(irrelevant < far);
        // 归一化域收敛在 [-1, 0]
        assertTrue(relevant >= -1.0 && far <= 0.0);
    }
}
