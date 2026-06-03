package com.campus.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crazy_thursday_registration")
public class CrazyThursdayRegistration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String weekKey;

    private Long userId;

    private Integer isWinner;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
