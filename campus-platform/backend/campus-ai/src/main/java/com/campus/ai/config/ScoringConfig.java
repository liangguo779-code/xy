package com.campus.ai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 重排序模型配置
 * BGE reranker（cross-encoder）：用于对检索结果重排序
 *
 * <p>极性说明（实测 bge-reranker-base，normalize=false）：{@link OnnxScoringModel} 原始输出为
 * 未归一化 raw logits，<b>越高越相关</b>（相关对 -0.63 / -2.24，无关对 -2.78 / -10.12），
 * 并非"负余弦 [-1,0] 越低越好"。为保持下游既有契约（{@code ConfidenceGrader} 阈值 -0.6/-0.3、
 * yml {@code score-threshold: -0.2}、组合排序均为"越低越好"），本配置在出口用
 * {@link NormalizedScoringModel} 把 logits 单调映射到 [-1, 0]（{@link #normalizeLogit}），
 * 使"越低越相关"在该域成立。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ScoringConfig {

    private final AiProperties props;

    /** 归一化尺度：logits / 该值后取负指数，把实测值域压进 [-1,0]（按下方锚点标定）。 */
    private static final double LOGIT_SCALE = 2.0d;

    /**
     * raw logit → [-1, 0] 相似度（越低越相关）。
     * 单调映射 {@code sim = -min(1, exp(logit / 2.0))}，按实测锚点标定档位：
     * -0.63 → -0.73（high）、-2.24 → -0.33（medium）、-2.78 → -0.25（low）、-10.12 → -0.01（low/触发重试）。
     */
    public static double normalizeLogit(double logit) {
        return -Math.min(1.0d, Math.exp(logit / LOGIT_SCALE));
    }

    /**
     * 重排序模型（cross-encoder）
     * 首次使用时从 HuggingFace 下载并缓存到本地
     * 如果模型无法下载，返回 NoOp 实现（跳过重排序）
     */
    @Bean
    public ScoringModel scoringModel() {
        Path modelDir = HuggingFaceModelLoader.underHome(
                props.getHome(), "models", "bge-reranker-base");
        Path modelFile = modelDir.resolve("model.onnx");
        Path tokenizerFile = modelDir.resolve("tokenizer.json");

        // 如果本地已有文件，直接使用
        if (Files.exists(modelFile) && Files.exists(tokenizerFile)) {
            try {
                log.info("Loading BGE reranker (cross-encoder) from {}", modelDir);
                // 出口归一化：raw logits 越高越好 → [-1,0] 越低越好，对齐下游阈值契约
                return new NormalizedScoringModel(
                        new OnnxScoringModel(modelFile.toString(), tokenizerFile.toString()));
            } catch (Exception e) {
                log.warn("BGE reranker 加载失败: {} — 重排序将跳过", e.getMessage());
                return new NoOpScoringModel();
            }
        }

        log.warn("⚠️  BGE reranker 模型不存在 ({}), 跳过重排序", modelDir);
        log.warn("   可运行: bash scripts/download-models.sh");
        return new NoOpScoringModel();
    }

    /**
     * 出口归一化包装：把底层模型的 raw logit 映射为 [-1,0]（越低越相似）的分数。
     * 使 ConfidenceGrader 阈值（-0.6/-0.3）与 yml score-threshold（-0.2）语义成立。
     */
    public static class NormalizedScoringModel implements ScoringModel {

        private final ScoringModel delegate;

        public NormalizedScoringModel(ScoringModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
            List<Double> raw = delegate.scoreAll(segments, query).content();
            List<Double> normalized = new ArrayList<>(raw.size());
            for (Double logit : raw) normalized.add(normalizeLogit(logit));
            return Response.from(normalized);
        }
    }

    /**
     * 重排序模型无法下载时的空安全兜底实现。
     *
     * <p>该类同时是"降级运行"的标记（P1-12）：恒等分（0, -1, -2, …）会让
     * 置信度分级失真（top0 判 low 全量跳过 LLM / 靠后段误判 high），
     * {@code RagOrchestrator} 通过 instanceof 感知降级并强制 medium/low 分级。
     */
    public static class NoOpScoringModel implements ScoringModel {
        @Override
        public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
            // 恒等分数 -i：在"越低越好"域里靠前片段分数更低，保持原始排序，不进行重排序
            List<Double> scores = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                scores.add((double) -i);
            }
            return Response.from(scores);
        }
    }
}
