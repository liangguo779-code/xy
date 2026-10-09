package com.campus.forum.document;

import com.campus.forum.entity.Post;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子的 Elasticsearch 文档结构。
 * 倒排索引字段：title / content（ik 中文分词），category 精确过滤。
 */
@Data
public class PostDocument {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String category;
    private Integer isTop;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime createTime;

    public static PostDocument fromPost(Post post) {
        PostDocument doc = new PostDocument();
        doc.setId(post.getId());
        doc.setUserId(post.getUserId());
        doc.setTitle(post.getTitle());
        doc.setContent(post.getContent());
        doc.setCategory(post.getCategory());
        doc.setIsTop(post.getIsTop());
        doc.setViewCount(post.getViewCount());
        doc.setLikeCount(post.getLikeCount());
        doc.setCommentCount(post.getCommentCount());
        doc.setCreateTime(post.getCreateTime());
        return doc;
    }
}
