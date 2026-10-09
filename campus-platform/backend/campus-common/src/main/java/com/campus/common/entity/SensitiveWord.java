package com.campus.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词库，对应 sensitive_word 表（本地关键词过滤的全部词源）。
 * 覆盖广告/辱骂/不良价值观等类目（如代考、代写论文），发布时由
 * ContentFilterService 同步匹配，按 level 执行差异化处置策略。
 */
@Data
@TableName("sensitive_word")
public class SensitiveWord {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 敏感词 */
    private String word;

    /** 类目: 政治/辱骂/色情/广告/谣言/隐私/其他 */
    private String category;

    /** 处置级别: 1-拒绝发布 2-替换*** 3-送审（发布但进人审队列） */
    private Integer level;

    /** 1-启用 0-停用 */
    private Integer enabled;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
