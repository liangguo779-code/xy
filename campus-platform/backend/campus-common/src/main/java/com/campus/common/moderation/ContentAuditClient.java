package com.campus.common.moderation;

import java.util.List;

/**
 * AI 审核 API 抽象（"机器自动拦截"第二道防线，举报后触发）。
 * 默认实现为 campus-ai 的 LLM 结构化裁决，可替换为第三方内容安全 API
 * （阿里云内容安全/网易易盾等），业务侧只依赖本接口。
 */
public interface ContentAuditClient {

    /**
     * 对内容做违规判定。
     *
     * @param content 待审内容
     * @param reason  举报理由（作为判定上下文）
     * @return 结构化裁决；调用失败应抛异常，由编排层降级转人审
     */
    AiVerdict audit(String content, String reason);

    /** AI 裁决结果。 */
    record AiVerdict(
            /** 0-合规 1-违规 2-不确定 */
            int verdict,
            /** 置信度 0-1 */
            double confidence,
            /** 命中类目（色情/政治/暴恐违禁/广告/谩骂/不良价值观/其他） */
            List<String> categories,
            /** 严重程度 1-3 */
            int severity,
            /** 判定理由 */
            String reason) {

        public static AiVerdict compliant(double confidence, String reason) {
            return new AiVerdict(0, confidence, List.of(), 0, reason);
        }

        public static AiVerdict violation(double confidence, List<String> categories, int severity, String reason) {
            return new AiVerdict(1, confidence, categories, severity, reason);
        }

        public static AiVerdict uncertain(String reason) {
            return new AiVerdict(2, 0, List.of(), 0, reason);
        }
    }
}
