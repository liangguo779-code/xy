package com.campus.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 封禁记录实体，对应 ban_record 表。
 * 支持按用户或 IP 封禁特定能力，同一目标同类型重复封禁会先解除旧记录。
 */
@Data
@TableName("ban_record")
public class BanRecord {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 封禁类型: user/ip */
    private String targetType;

    /** 封禁目标: 用户ID或IP地址 */
    private String targetValue;

    /** 封禁能力: all/trade/message/forum */
    private String banType;

    /** 封禁原因 */
    private String reason;

    /** 封禁截止时间 */
    private LocalDateTime banUntil;

    /** 操作人ID */
    private Long operatorId;

    /** 状态: 0-已解除 1-生效中 */
    private Integer status;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入/更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
