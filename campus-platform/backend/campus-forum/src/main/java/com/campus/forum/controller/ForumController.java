package com.campus.forum.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;
import com.campus.forum.service.ForumService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 论坛-帖子与评论接口：浏览可匿名，发帖/点赞/收藏/评论等操作需登录 */
@RestController
@RequestMapping("/api/forum")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;

    /**
     * 分页查询帖子列表，支持多维筛选与排序。
     * 筛选：category 分类、keyword 关键词、startTime/endTime 创建时间范围（yyyy-MM-dd）；
     * 排序：sortBy = latest（默认，置顶+最新）| hot（点赞/评论/浏览加权热度）。
     */
    @GetMapping("/posts")
    public R<Page<Post>> listPosts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate startTime,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        java.time.LocalDateTime start = startTime != null ? startTime.atStartOfDay() : null;
        // endTime 传入的是日期，转成次日零点作为不含上界
        java.time.LocalDateTime end = endTime != null ? endTime.plusDays(1).atStartOfDay() : null;
        return R.ok(forumService.listPosts(category, keyword, sortBy, start, end, page, size));
    }

    /** 热门帖子 top-N（Redis 热度 zset + 列表缓存） */
    @GetMapping("/posts/hot")
    public R<List<Post>> getHotPosts(@RequestParam(defaultValue = "10") int size) {
        return R.ok(forumService.getHotPosts(size));
    }

    /** 获取帖子详情并累加浏览量（未登录时不回填点赞状态） */
    @GetMapping("/posts/{id}")
    public R<Post> getPost(@PathVariable Long id) {
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return R.ok(forumService.getPostDetail(id, userId));
    }

    /** 发帖 */
    @PostMapping("/posts")
    public R<Post> createPost(@RequestBody Post post) {
        return R.ok(forumService.createPost(StpUtil.getLoginIdAsLong(), post));
    }

    /** 编辑帖子（仅作者可操作） */
    @PutMapping("/posts/{id}")
    public R<Post> updatePost(@PathVariable Long id, @RequestBody Post post) {
        return R.ok(forumService.updatePost(StpUtil.getLoginIdAsLong(), id, post));
    }

    /** 删除帖子（仅作者可操作，逻辑删除） */
    @DeleteMapping("/posts/{id}")
    public R<Void> deletePost(@PathVariable Long id) {
        forumService.deletePost(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    /** 点赞/取消点赞帖子 */
    @PostMapping("/posts/{id}/like")
    public R<Boolean> likePost(@PathVariable Long id) {
        boolean liked = forumService.likePost(StpUtil.getLoginIdAsLong(), id);
        return R.ok(liked);
    }

    /** 收藏/取消收藏帖子 */
    @PostMapping("/posts/{id}/favorite")
    public R<Boolean> toggleFavorite(@PathVariable Long id) {
        boolean favorited = forumService.toggleFavorite(StpUtil.getLoginIdAsLong(), id);
        return R.ok(favorited);
    }

    /** 查询当前用户是否已收藏该帖子 */
    @GetMapping("/posts/{id}/favorite/status")
    public R<Boolean> getFavoriteStatus(@PathVariable Long id) {
        return R.ok(forumService.isFavorited(StpUtil.getLoginIdAsLong(), id));
    }

    /** 分页查询我的收藏帖子 */
    @GetMapping("/favorites")
    public R<Page<Post>> getMyFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(forumService.getMyFavorites(StpUtil.getLoginIdAsLong(), page, size));
    }

    /** 分页查询我发布的帖子 */
    @GetMapping("/posts/mine")
    public R<Page<Post>> getMyPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(forumService.getMyPosts(StpUtil.getLoginIdAsLong(), page, size));
    }

    /** 分页查询帖子评论（平铺列表，按时间正序，登录时批量回填点赞状态） */
    @GetMapping("/posts/{id}/comments")
    public R<Page<Comment>> getComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        // 未登录不回填 liked（匿名浏览），登录后批量回填，与评论树接口口径一致
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return R.ok(forumService.getComments(id, userId, page, size));
    }

    /** 获取帖子评论树（含楼中楼回复） */
    @GetMapping("/posts/{id}/comments/tree")
    public R<List<Comment>> getCommentTree(@PathVariable Long id) {
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return R.ok(forumService.getCommentTree(id, userId));
    }

    /** 点赞/取消点赞评论 */
    @PostMapping("/comments/{id}/like")
    public R<Boolean> likeComment(@PathVariable Long id) {
        boolean liked = forumService.likeComment(StpUtil.getLoginIdAsLong(), id);
        return R.ok(liked);
    }

    /**
     * 删除评论（逻辑删除，连同楼中楼一并删除并回滚评论计数与热度）。
     * 权限：评论作者本人或帖子作者（P2-11 交互闭环）。
     */
    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable Long id) {
        forumService.deleteComment(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    /**
     * 发表评论或回复
     * @param id 帖子 ID
     * @param parentId 父评论 ID，0 表示一级评论
     */
    @PostMapping("/posts/{id}/comments")
    public R<Comment> createComment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Long parentId,
            @RequestBody CommentReq req) {
        return R.ok(forumService.createComment(StpUtil.getLoginIdAsLong(), id, parentId, req.content, req.images));
    }

    /** 发表评论请求 */
    public static class CommentReq {
        /** 评论内容 */
        public String content;
        /** 图片地址，JSON 数组字符串 */
        public String images;
    }
}
