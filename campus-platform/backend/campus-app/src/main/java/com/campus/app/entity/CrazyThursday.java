package com.campus.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 疯狂星期四活动场次，按周维度记录名额、开奖状态与中奖用户 */
@Data
@TableName("crazy_thursday")
public class CrazyThursday {

    /** 主键ID */
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

    /** 开奖时间 */
    private LocalDateTime drawTime;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
