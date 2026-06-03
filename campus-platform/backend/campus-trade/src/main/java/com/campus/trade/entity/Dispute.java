package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dispute")
public class Dispute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long reporterId;
    private String reason;
    private String evidenceImages;
    private Integer status;
    private String result;
    private Long handlerId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
