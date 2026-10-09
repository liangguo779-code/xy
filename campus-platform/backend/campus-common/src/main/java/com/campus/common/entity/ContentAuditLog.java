package com.campus.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 内容安全审计流水，对应 content_audit_log 表。
 * 每次内容状态流转记一条：词库命中 / 机审判定 / 人工修正，
 * 是"机器自动拦截 + 人工复核"闭环的唯一事实来源，KPI 指标（识别准确率、
 * 人工复核减负率）由本表统计得出。
 */
@Data
@TableName("content_audit_log")
public class ContentAuditLog {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标类型: post/comment/message/goods */
    private String targetType;

    /** 目标 ID */
    private Long targetId;

    /** 触发方式: 1-发布词库 2-举报机审 3-人审 4-巡查 5-申诉 */
    private Integer triggerType;

    /** 内容快照（超长截断） */
    private String content;

    /** 命中的敏感词，逗号分隔 */
    private String hitWords;

    /** AI 判定: 0-合规 1-违规 2-不确定 */
    private Integer aiVerdict;

    /** AI 置信度 0-1 */
    private BigDecimal aiConfidence;

    /** AI 判定理由 */
    private String aiReason;

    /** 流转后的状态（对应该对象 status 枚举，评论为 0-4） */
    private Integer finalStatus;

    /** 人工操作者 ID，机器判定为 null */
    private Long reviewerId;

    /** 人工备注/修正理由 */
    private String reviewNote;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
