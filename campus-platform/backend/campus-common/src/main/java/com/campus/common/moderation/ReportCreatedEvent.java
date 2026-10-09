package com.campus.common.moderation;

/**
 * 举报创建事件：ReportServiceImpl 落库后发布，
 * campus-app 的内容安全编排器监听并触发 AI 审核流程。
 * 放 common 包使 trade（发布方）与 app（消费方）无需互相依赖。
 */
public record ReportCreatedEvent(
        Long reportId,
        String targetType,
        Long targetId,
        Long reporterId,
        String reason) {
}
