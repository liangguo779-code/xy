package com.campus.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.entity.BanRecord;
import com.campus.common.exception.BusinessException;
import com.campus.common.service.BanService;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;
import com.campus.forum.mapper.CommentMapper;
import com.campus.forum.mapper.PostMapper;
import com.campus.forum.service.ForumService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ForumServiceImpl extends ServiceImpl<PostMapper, Post> implements ForumService {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final BanService banService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Page<Post> listPosts(String category, String keyword, int page, int size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 1)
                .orderByDesc(Post::getIsTop)
                .orderByDesc(Post::getCreateTime);
        if (StringUtils.hasText(category)) wrapper.eq(Post::getCategory, category);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Post::getTitle, keyword).or().like(Post::getContent, keyword));
        }
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Post getPostDetail(Long id) {
        Post post = getById(id);
        if (post == null || post.getStatus() != 1) throw new BusinessException("帖子不存在");
        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, id).setSql("view_count = view_count + 1"));
        return post;
    }

    @Override
    public Post createPost(Long userId, Post post) {
        // 检查论坛封禁
        BanRecord ban = banService.checkUserBan(userId, "forum");
        if (ban != null) {
            throw new BusinessException("您的论坛功能已被封禁，原因：" + ban.getReason()
                    + "，解封时间：" + ban.getBanUntil());
        }

        post.setUserId(userId);
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setIsTop(0);
        post.setStatus(1);
        save(post);
        return post;
    }

    @Override
    @Transactional
    public void likePost(Long userId, Long postId) {
        // 使用 Redis 记录点赞状态，防止重复点赞
        String likeKey = "post_like:" + postId + ":" + userId;
        Boolean alreadyLiked = redisTemplate.hasKey(likeKey);
        if (Boolean.TRUE.equals(alreadyLiked)) {
            throw new BusinessException("已经点赞过了");
        }
        redisTemplate.opsForValue().set(likeKey, "1");
        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, postId).setSql("like_count = like_count + 1"));
    }

    @Override
    public Page<Comment> getComments(Long postId, int page, int size) {
        return commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .eq(Comment::getStatus, 1)
                        .orderByAsc(Comment::getCreateTime));
    }

    @Override
    public Comment createComment(Long userId, Long postId, Long parentId, String content) {
        // 检查论坛封禁
        BanRecord ban = banService.checkUserBan(userId, "forum");
        if (ban != null) {
            throw new BusinessException("您的论坛功能已被封禁，原因：" + ban.getReason()
                    + "，解封时间：" + ban.getBanUntil());
        }

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId != null ? parentId : 0L);
        comment.setContent(content);
        comment.setLikeCount(0);
        comment.setStatus(1);
        commentMapper.insert(comment);

        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, postId).setSql("comment_count = comment_count + 1"));
        return comment;
    }
}
