package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品浏览历史，对应 browse_history 表，用于"最近浏览"与个性化推荐 */
@Data
@TableName("browse_history")
public class BrowseHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 浏览者用户 ID */
    private Long userId;

    /** 被浏览的商品 ID */
    private Long goodsId;

    /** 浏览时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
