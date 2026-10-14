package com.campus.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 管理员发布热卖商品请求（仅 ADMIN 可调用，发布即上架，无需审核） */
@Data
public class PublishHotGoodsReq {

    /** 商品标题 */
    @NotBlank(message = "标题不能为空")
    private String title;

    /** 商品描述 */
    private String description;

    /** 售价（元） */
    @NotNull(message = "售价不能为空")
    private BigDecimal price;

    /** 原价（元），展示折扣用 */
    private BigDecimal originalPrice;

    /** 分类 ID */
    private Long categoryId;

    /** 商品图片（JSON 数组，存图片 URL 列表） */
    private String images;

    /** 商品视频 URL */
    private String videoUrl;

    /** 成色: 全新/几乎全新/良好/一般 */
    private String condition;

    /** 发货点（校内位置描述），配送单取货地址用 */
    @NotBlank(message = "发货点不能为空")
    private String location;

    /** 初始可售库存 */
    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    /** 单人限购数量，NULL/0 不限购 */
    private Integer purchaseLimit;
}
