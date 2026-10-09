package com.campus.trade.document;

import com.campus.trade.entity.Goods;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 商品搜索文档，映射 ES 索引结构，由 Goods 实体转换而来供全文检索使用 */
@Data
public class GoodsDocument {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 商品ID（ES 文档主键） */
    private Long id;
    /** 发布者用户ID */
    private Long userId;
    /** 商品标题（参与检索） */
    private String title;
    /** 商品描述（参与检索） */
    private String description;
    /** 售价（元） */
    private Double price;
    /** 原价（元），可空 */
    private Double originalPrice;
    /** 分类ID */
    private Long categoryId;
    /** 分类名称 */
    private String category;
    /** 商品图片URL列表（JSON数组字符串） */
    private String images;
    /** 成色描述（如 全新/九成新） */
    private String condition;
    /** 交易地点/校区位置 */
    private String location;
    /** 商品类型：0-出售 1-求购 */
    private Integer type;
    /** 商品状态：0-已上架 1-已下架 2-已售出 3-待审核/已预订 */
    private Integer status;
    /** 占位持有者（会话ID/订单ID），非空表示"已预订"——检索可见；空且 status=3 为待审核，不参与检索 */
    private Long reserveRef;
    /** 浏览次数 */
    private Integer viewCount;
    /** 想要人数 */
    private Integer wantCount;
    /** 点赞次数 */
    private Integer likeCount;
    /** 最近刷新时间（ISO 格式字符串，参与排序） */
    private String refreshTime;
    /** 发布时间（ISO 格式字符串） */
    private String createTime;

    /** 实体转 ES 文档，日期统一格式化为 ISO 字符串以便索引与排序 */
    public static GoodsDocument fromGoods(Goods goods) {
        GoodsDocument doc = new GoodsDocument();
        doc.setId(goods.getId());
        doc.setUserId(goods.getUserId());
        doc.setTitle(goods.getTitle());
        doc.setDescription(goods.getDescription());
        doc.setPrice(goods.getPrice() != null ? goods.getPrice().doubleValue() : null);
        doc.setOriginalPrice(goods.getOriginalPrice() != null ? goods.getOriginalPrice().doubleValue() : null);
        doc.setCategoryId(goods.getCategoryId());
        doc.setCategory(goods.getCategory());
        doc.setImages(goods.getImages());
        doc.setCondition(goods.getCondition());
        doc.setLocation(goods.getLocation());
        doc.setType(goods.getType());
        doc.setStatus(goods.getStatus());
        doc.setReserveRef(goods.getReserveRef());
        doc.setViewCount(goods.getViewCount());
        doc.setWantCount(goods.getWantCount());
        doc.setLikeCount(goods.getLikeCount());
        doc.setRefreshTime(goods.getRefreshTime() != null ? goods.getRefreshTime().format(DT_FMT) : null);
        doc.setCreateTime(goods.getCreateTime() != null ? goods.getCreateTime().format(DT_FMT) : null);
        return doc;
    }
}
