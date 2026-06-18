package com.campus.forum.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.R;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;
import com.campus.forum.mapper.CommentMapper;
import com.campus.forum.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/forum")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminForumController {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;

    @GetMapping("/posts")
    public R<Page<Post>> listPosts(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Post::getStatus, status);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Post::getTitle, keyword).or().like(Post::getContent, keyword));
        }
        wrapper.orderByDesc(Post::getCreateTime);
        return R.ok(postMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @PutMapping("/posts/{id}/top")
    public R<Void> toggleTop(@PathVariable Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) throw new BusinessException("帖子不存在");
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, id)
                .set(Post::getIsTop, post.getIsTop() == 1 ? 0 : 1));
        return R.ok();
    }

    @PutMapping("/posts/{id}/hide")
    public R<Void> hidePost(@PathVariable Long id) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, id)
                .set(Post::getStatus, 0));
        return R.ok();
    }

    @DeleteMapping("/posts/{id}")
    public R<Void> deletePost(@PathVariable Long id) {
        postMapper.deleteById(id);
        return R.ok();
    }

    @PutMapping("/posts/{id}/restore")
    public R<Void> restorePost(@PathVariable Long id) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, id)
                .set(Post::getStatus, 1));
        return R.ok();
    }

    @GetMapping("/posts/{id}/comments")
    public R<Page<Comment>> listComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return R.ok(commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, id)
                        .orderByDesc(Comment::getCreateTime)));
    }

    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable Long id) {
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .eq(Comment::getId, id)
                .set(Comment::getStatus, 0));
        return R.ok();
    }
}
