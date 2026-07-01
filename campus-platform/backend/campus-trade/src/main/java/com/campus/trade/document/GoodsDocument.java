package com.campus.trade.document;

import com.campus.trade.entity.Goods;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class GoodsDocument {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private Double price;
    private Double originalPrice;
    private Long categoryId;
    private String category;
    private String images;
    private String condition;
    private String location;
    private Integer type;
    private Integer status;
    private Integer viewCount;
    private Integer wantCount;
    private Integer likeCount;
    private String refreshTime;
    private String createTime;

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
        doc.setViewCount(goods.getViewCount());
        doc.setWantCount(goods.getWantCount());
        doc.setLikeCount(goods.getLikeCount());
        doc.setRefreshTime(goods.getRefreshTime() != null ? goods.getRefreshTime().format(DT_FMT) : null);
        doc.setCreateTime(goods.getCreateTime() != null ? goods.getCreateTime().format(DT_FMT) : null);
        return doc;
    }
}
