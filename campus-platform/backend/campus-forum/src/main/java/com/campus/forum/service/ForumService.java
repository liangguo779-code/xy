package com.campus.forum.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;

import java.util.List;

/** 论坛业务接口：帖子增删改查、评论与楼中楼、点赞、收藏 */
public interface ForumService extends IService<Post> {
    /**
     * 分页查询正常状态帖子，支持多维筛选与排序。
     *
     * @param category 分类过滤（可空）
     * @param keyword 关键词（走 ES 全文检索，可空）
     * @param sortBy 排序：null/"latest"-最新优先，"hot"-热度优先
     * @param startTime 创建时间下限（含，可空）
     * @param endTime 创建时间上限（不含，可空）
     */
    Page<Post> listPosts(String category, String keyword, String sortBy,
                         java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
                         int page, int size);
    /** 获取帖子详情并累加浏览量，userId 非空时回填点赞状态 */
    Post getPostDetail(Long id, Long userId);
    /** 发帖（校验论坛封禁，初始化计数与状态） */
    Post createPost(Long userId, Post post);
    /** 编辑帖子，仅作者可操作 */
    Post updatePost(Long userId, Long id, Post post);
    /** 删除帖子（逻辑删除），仅作者可操作 */
    void deletePost(Long userId, Long id);
    /** 热门帖子 top-N（Redis zset 热度 + 列表缓存） */
    List<Post> getHotPosts(int size);
    /** 点赞/取消点赞帖子，返回 true-点赞 false-取消点赞 */
    boolean likePost(Long userId, Long postId);
    /**
     * 分页查询帖子的正常评论，按时间正序。
     *
     * @param userId 当前登录用户（可空），非空时批量回填每条评论的点赞状态 liked
     */
    Page<Comment> getComments(Long postId, Long userId, int page, int size);
    /** 获取帖子评论树（根评论挂在 children 下的楼中楼） */
    List<Comment> getCommentTree(Long postId, Long userId);
    /** 发表评论或回复，parentId 为 0 表示一级评论 */
    Comment createComment(Long userId, Long postId, Long parentId, String content, String images);
    /** 点赞/取消点赞评论，返回 true-点赞 false-取消点赞 */
    boolean likeComment(Long userId, Long commentId);
    /**
     * 删除评论（逻辑删除）：评论作者或帖子作者可操作，
     * 连同楼中楼一起删除，并回滚帖子评论计数与热度分。
     */
    void deleteComment(Long userId, Long commentId);
    /** 管理端删除评论：跳过作者校验，回滚逻辑与 {@link #deleteComment} 一致 */
    void adminDeleteComment(Long commentId);
    /** 收藏/取消收藏帖子，返回 true-收藏 false-取消收藏 */
    boolean toggleFavorite(Long userId, Long postId);
    /** 查询用户是否已收藏该帖子 */
    boolean isFavorited(Long userId, Long postId);
    /** 分页查询用户收藏的帖子（已删除的帖子不展示） */
    Page<Post> getMyFavorites(Long userId, int page, int size);
    /** 分页查询用户自己发布的正常帖子 */
    Page<Post> getMyPosts(Long userId, int page, int size);
}
