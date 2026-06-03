package com.campus.forum.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;
import com.campus.forum.service.ForumService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
        return R.ok(forumService.getPostDetail(id));
    }

    @PostMapping("/posts")
    public R<Post> createPost(@RequestBody Post post) {
        return R.ok(forumService.createPost(StpUtil.getLoginIdAsLong(), post));
    }

    @PostMapping("/posts/{id}/like")
    public R<Void> likePost(@PathVariable Long id) {
        forumService.likePost(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @GetMapping("/posts/{id}/comments")
    public R<Page<Comment>> getComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return R.ok(forumService.getComments(id, page, size));
    }

    @PostMapping("/posts/{id}/comments")
    public R<Comment> createComment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Long parentId,
            @RequestBody CommentReq req) {
        return R.ok(forumService.createComment(StpUtil.getLoginIdAsLong(), id, parentId, req.content));
    }

    public static class CommentReq {
        public String content;
    }
}
