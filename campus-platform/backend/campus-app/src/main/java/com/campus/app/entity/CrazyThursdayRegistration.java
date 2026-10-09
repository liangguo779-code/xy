package com.campus.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 疯狂星期四报名记录，同一用户同一周仅允许报名一次 */
@Data
@TableName("crazy_thursday_registration")
public class CrazyThursdayRegistration {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 周标识: 2026-W23 */
    private String weekKey;

    /** 报名用户ID */
    private Long userId;

    /** 0-未中奖 1-已中奖 */
    private Integer isWinner;

    /** 报名时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
