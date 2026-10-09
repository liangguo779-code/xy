package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品，对应 goods 表，校园二手交易的核心商品信息 */
@Data
@TableName("goods")
public class Goods {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布者用户 ID */
    private Long userId;

    /** 商品标题 */
    private String title;

    /** 商品描述 */
    private String description;

    /** 售价（元） */
    private BigDecimal price;

    /** 原价（元），用于展示折扣信息 */
    private BigDecimal originalPrice;

    /** 分类 ID，关联 category.id */
    private Long categoryId;

    /** 分类名称冗余字段，便于列表直接展示 */
    private String category;

    /** 商品图片（JSON 数组，存图片 URL 列表） */
    private String images;

    /** 商品视频 URL */
    private String videoUrl;

    /** 成色: 全新/几乎全新/良好/一般 */
    @TableField("`condition`")
    private String condition;

    /** 交易地点（校内位置描述） */
    private String location;

    /** 0-出售 1-求购 */
    @TableField("`type`")
    private Integer type;

    /**
     * 商品状态：0-已上架 1-已下架 2-已售出 3-待审核/已预订。
     *
     * <p>注意 3 是双义值（历史兼容，不拆分枚举值）：管理审核侧表示"待审核"，
     * 交易流程同时用作"已预订"锁定位（发起交易请求时 0→3 占位，完成转 2、拒绝/过期/取消回补转 0）。
     * 任何把 3 改回 0 的操作必须先确认无活跃订单且无请求占位
     * （见 AdminGoodsController 审核接口、OrderTimeoutService 回补逻辑、GoodsReservationService）。
     */
    private Integer status;

    /**
     * 占位持有者：发起交易请求时写入会话 ID（chat_session.id），建单后转移为订单 ID（order.id），
     * 无占位为 NULL。所有恢复（拒绝/过期/取消）必须带持有者谓词（WHERE reserve_ref = 自己），
     * 防止旧请求的释放把新占位翻回在售造成一货两卖（P0-3 同类防护）。
     */
    private Long reserveRef;

    /** 下架原因 */
    private String offReason;

    /** 浏览数 */
    private Integer viewCount;

    /** 想要/求购数 */
    private Integer wantCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 价格变动历史（JSON），供价格走势展示 */
    private String priceHistory;

    /** 最近刷新时间，手动刷新可提升排序权重 */
    private LocalDateTime refreshTime;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
