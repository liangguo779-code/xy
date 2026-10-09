package com.campus.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统配置实体，对应 sys_config 表。以 key-value 形式存储可动态调整的参数
 * （如公告、开关、阈值），改配置无需重启服务。
 */
@Data
@TableName("sys_config")
public class SysConfig {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 配置键（唯一，业务侧按 key 读取） */
    private String configKey;
    /** 配置值（字符串形式，数值/JSON 由调用方解析） */
    private String configValue;
    /** 配置项说明，便于管理端理解用途 */
    private String description;
    /** 更新时间（插入/更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
