package com.campus.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 站内通知实体，对应 notification 表。
 * 由各业务模块通过 NotificationService.send 写入，用户在消息中心查看。
 */
@Data
@TableName("notification")
public class Notification {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 接收用户 ID */
    private Long userId;
    /** 通知类型：order_confirmed/order_cancelled/order_completed/forum_like/forum_comment/forum_reply/price_drop 等 */
    private String type;
    /** 通知标题 */
    private String title;
    /** 通知正文 */
    private String content;
    /** 扩展数据（JSON 字符串），如 {"orderId":1,"verifyCode":"1234"}，供前端跳转详情用 */
    private String extra;
    /** 0-未读 1-已读 */
    private Integer isRead;
    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
