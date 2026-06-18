package com.campus.trade.document;

import com.campus.trade.entity.Goods;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GoodsDocument {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
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
    private LocalDateTime refreshTime;
    private LocalDateTime createTime;

    public static GoodsDocument fromGoods(Goods goods) {
        GoodsDocument doc = new GoodsDocument();
        doc.setId(goods.getId());
        doc.setUserId(goods.getUserId());
        doc.setTitle(goods.getTitle());
        doc.setDescription(goods.getDescription());
        doc.setPrice(goods.getPrice());
        doc.setOriginalPrice(goods.getOriginalPrice());
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
        doc.setRefreshTime(goods.getRefreshTime());
        doc.setCreateTime(goods.getCreateTime());
        return doc;
    }
}
