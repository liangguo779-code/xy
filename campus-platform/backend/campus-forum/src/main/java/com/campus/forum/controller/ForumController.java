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

@RestController
@RequestMapping("/api/forum")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;

    @GetMapping("/posts")
    public R<Page<Post>> listPosts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(forumService.listPosts(category, keyword, page, size));
    }

    @GetMapping("/posts/{id}")
    public R<Post> getPost(@PathVariable Long id) {
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return R.ok(forumService.getPostDetail(id, userId));
    }

    @PostMapping("/posts")
    public R<Post> createPost(@RequestBody Post post) {
        return R.ok(forumService.createPost(StpUtil.getLoginIdAsLong(), post));
    }

    @PutMapping("/posts/{id}")
    public R<Post> updatePost(@PathVariable Long id, @RequestBody Post post) {
        return R.ok(forumService.updatePost(StpUtil.getLoginIdAsLong(), id, post));
    }

    @DeleteMapping("/posts/{id}")
    public R<Void> deletePost(@PathVariable Long id) {
        forumService.deletePost(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @PostMapping("/posts/{id}/like")
    public R<Boolean> likePost(@PathVariable Long id) {
        boolean liked = forumService.likePost(StpUtil.getLoginIdAsLong(), id);
        return R.ok(liked);
    }

    @PostMapping("/posts/{id}/favorite")
    public R<Boolean> toggleFavorite(@PathVariable Long id) {
        boolean favorited = forumService.toggleFavorite(StpUtil.getLoginIdAsLong(), id);
        return R.ok(favorited);
    }

    @GetMapping("/posts/{id}/favorite/status")
    public R<Boolean> getFavoriteStatus(@PathVariable Long id) {
        return R.ok(forumService.isFavorited(StpUtil.getLoginIdAsLong(), id));
    }

    @GetMapping("/favorites")
    public R<Page<Post>> getMyFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(forumService.getMyFavorites(StpUtil.getLoginIdAsLong(), page, size));
    }

    @GetMapping("/posts/mine")
    public R<Page<Post>> getMyPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(forumService.getMyPosts(StpUtil.getLoginIdAsLong(), page, size));
    }

    @GetMapping("/posts/{id}/comments")
    public R<Page<Comment>> getComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return R.ok(forumService.getComments(id, page, size));
    }

    @GetMapping("/posts/{id}/comments/tree")
    public R<List<Comment>> getCommentTree(@PathVariable Long id) {
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return R.ok(forumService.getCommentTree(id, userId));
    }

    @PostMapping("/comments/{id}/like")
    public R<Boolean> likeComment(@PathVariable Long id) {
        boolean liked = forumService.likeComment(StpUtil.getLoginIdAsLong(), id);
        return R.ok(liked);
    }

    @PostMapping("/posts/{id}/comments")
    public R<Comment> createComment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Long parentId,
            @RequestBody CommentReq req) {
        return R.ok(forumService.createComment(StpUtil.getLoginIdAsLong(), id, parentId, req.content, req.images));
    }

    public static class CommentReq {
        public String content;
        public String images;
    }
}
