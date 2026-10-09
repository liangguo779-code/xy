package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 交易纠纷，对应 dispute 表，买卖双方对订单发起的争议及平台裁决 */
@Data
@TableName("dispute")
public class Dispute {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 纠纷关联的订单 ID */
    private Long orderId;
    /** 发起纠纷的用户 ID */
    private Long reporterId;
    /** 纠纷理由描述 */
    private String reason;
    /** 凭证图片（JSON 数组，存图片 URL 列表） */
    private String evidenceImages;
    /** 状态：0-待处理 2-已解决 3-已驳回 */
    private Integer status;
    /** 平台处理结论 */
    private String result;
    /** 处理该纠纷的管理员 ID */
    private Long handlerId;
    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
