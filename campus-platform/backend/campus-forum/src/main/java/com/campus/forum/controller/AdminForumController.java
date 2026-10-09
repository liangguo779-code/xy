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

/** 管理后台-论坛管理：帖子与评论的审核、置顶、隐藏与删除 */
@RestController
@RequestMapping("/api/admin/forum")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminForumController {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final com.campus.forum.service.PostIndexService postIndexService;
    private final com.campus.forum.service.PostHotService postHotService;
    private final com.campus.forum.service.PostTopService postTopService;
    private final com.campus.forum.service.ForumService forumService;

    /** 分页查询帖子（可按状态与标题/内容关键字筛选） */
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

    /** 切换帖子置顶状态（已置顶则取消，否则置顶） */
    @PutMapping("/posts/{id}/top")
    public R<Void> toggleTop(@PathVariable Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) throw new BusinessException("帖子不存在");
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, id)
                .set(Post::getIsTop, post.getIsTop() == 1 ? 0 : 1));
        // 同步 ES 索引中的置顶标记
        Post updated = postMapper.selectById(id);
        if (updated != null) {
            postIndexService.indexPost(updated);
        }
        // 置顶集合变化，失效置顶列表缓存
        postTopService.invalidate();
        return R.ok();
    }

    /** 隐藏帖子（status 置 0，用户端不再展示） */
    @PutMapping("/posts/{id}/hide")
    public R<Void> hidePost(@PathVariable Long id) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, id)
                .set(Post::getStatus, 0));
        // 从搜索索引与热度缓存移除
        postIndexService.deletePost(id);
        postHotService.onDelete(id);
        postTopService.invalidate();
        return R.ok();
    }

    /** 物理删除帖子（与用户端的逻辑删除不同） */
    @DeleteMapping("/posts/{id}")
    public R<Void> deletePost(@PathVariable Long id) {
        postMapper.deleteById(id);
        postIndexService.deletePost(id);
        postHotService.onDelete(id);
        postTopService.invalidate();
        return R.ok();
    }

    /** 恢复已隐藏的帖子（status 置 1） */
    @PutMapping("/posts/{id}/restore")
    public R<Void> restorePost(@PathVariable Long id) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, id)
                .set(Post::getStatus, 1));
        // 重新写入搜索索引
        Post restored = postMapper.selectById(id);
        if (restored != null) {
            postIndexService.indexPost(restored);
        }
        // 恢复的帖子可能带置顶标记，失效置顶列表缓存
        postTopService.invalidate();
        return R.ok();
    }

    /** 全量重建帖子搜索索引（数据修复/ES 重建后使用） */
    @PostMapping("/reindex")
    public R<Void> reindex() {
        postIndexService.reindexAll();
        return R.ok();
    }

    /** 分页查询指定帖子下的评论（管理端，含已删除） */
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

    /**
     * 删除评论（逻辑删除，status 置 0）。
     * 走服务统一逻辑：连同楼中楼一并删除，并回滚帖子评论计数与热度分（P2-11）。
     */
    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable Long id) {
        forumService.adminDeleteComment(id);
        return R.ok();
    }
}
