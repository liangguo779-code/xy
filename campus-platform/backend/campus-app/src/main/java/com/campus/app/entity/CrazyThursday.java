package com.campus.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crazy_thursday")
public class CrazyThursday {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 周标识: 2026-W23 */
    private String weekKey;

    /** 最大名额 */
    private Integer maxSlots;

    /** 中奖用户ID */
    private Long winnerId;

    /** 0-报名中 1-已开奖 */
    private Integer status;

    private LocalDateTime drawTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
