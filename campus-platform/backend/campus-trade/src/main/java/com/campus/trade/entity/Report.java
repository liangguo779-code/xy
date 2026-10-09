package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 举报记录，对应 report 表，用户对商品/用户/消息/帖子/评论的举报 */
@Data
@TableName("report")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 举报者用户 ID */
    private Long reporterId;
    /** 举报对象类型：goods-商品 user-用户 message-消息 post-帖子 comment-评论 */
    private String targetType;
    /** 举报对象 ID */
    private Long targetId;
    /** 举报理由 */
    private String reason;
    /** 举报凭证（图片 URL 或 JSON 数组） */
    private String evidence;
    /** 状态：0-待处理 1-已处理 2-已驳回 */
    private Integer status;
    /** 平台处理结论 */
    private String result;
    /** 举报时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
