package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 商品收藏，对应 favorite 表，用户收藏的商品记录 */
@Data
@TableName("favorite")
public class Favorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 收藏者用户 ID */
    private Long userId;
    /** 被收藏的商品 ID */
    private Long goodsId;
    /** 收藏时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
