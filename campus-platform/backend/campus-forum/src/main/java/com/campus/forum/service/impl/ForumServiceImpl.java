package com.campus.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.entity.BanRecord;
import com.campus.common.exception.BusinessException;
import com.campus.common.service.BanService;
import com.campus.common.service.NotificationService;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;
import com.campus.forum.entity.PostFavorite;
import com.campus.forum.mapper.CommentMapper;
import com.campus.forum.mapper.PostFavoriteMapper;
import com.campus.forum.mapper.PostMapper;
import com.campus.forum.service.ForumService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ForumServiceImpl extends ServiceImpl<PostMapper, Post> implements ForumService {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final BanService banService;
    private final NotificationService notificationService;
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
    public Post getPostDetail(Long id, Long userId) {
        Post post = getById(id);
        if (post == null || post.getStatus() != 1) throw new BusinessException("帖子不存在");
        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, id).setSql("view_count = view_count + 1"));
        if (userId != null) {
            String likeKey = "post_like:" + id + ":" + userId;
            post.setLiked(Boolean.TRUE.equals(redisTemplate.hasKey(likeKey)));
        }
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
    public Post updatePost(Long userId, Long id, Post post) {
        Post existing = getById(id);
        if (existing == null || existing.getStatus() != 1) throw new BusinessException("帖子不存在");
        if (!existing.getUserId().equals(userId)) throw new BusinessException("只能编辑自己的帖子");

        existing.setTitle(post.getTitle());
        existing.setContent(post.getContent());
        existing.setCategory(post.getCategory());
        existing.setImages(post.getImages());
        updateById(existing);
        return existing;
    }

    @Override
    public void deletePost(Long userId, Long id) {
        Post existing = getById(id);
        if (existing == null || existing.getStatus() != 1) throw new BusinessException("帖子不存在");
        if (!existing.getUserId().equals(userId)) throw new BusinessException("只能删除自己的帖子");

        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, id).set(Post::getStatus, 0));
    }

    @Override
    @Transactional
    public boolean likePost(Long userId, Long postId) {
        String likeKey = "post_like:" + postId + ":" + userId;
        Boolean alreadyLiked = redisTemplate.hasKey(likeKey);
        if (Boolean.TRUE.equals(alreadyLiked)) {
            redisTemplate.delete(likeKey);
            update(new LambdaUpdateWrapper<Post>().eq(Post::getId, postId).setSql("like_count = GREATEST(like_count - 1, 0)"));
            return false;
        }
        redisTemplate.opsForValue().set(likeKey, "1");
        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, postId).setSql("like_count = like_count + 1"));
        // 通知帖子作者
        Post post = getById(postId);
        if (post != null && !post.getUserId().equals(userId)) {
            notificationService.send(post.getUserId(), "forum_like",
                    "帖子被点赞", "有人点赞了你的帖子「" + post.getTitle() + "」",
                    "{\"postId\":" + postId + "}");
        }
        return true;
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
    public List<Comment> getCommentTree(Long postId, Long userId) {
        List<Comment> all = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .eq(Comment::getStatus, 1)
                        .orderByAsc(Comment::getCreateTime));

        if (userId != null) {
            for (Comment c : all) {
                String key = "comment_like:" + c.getId() + ":" + userId;
                if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                    c.setLiked(true);
                }
            }
        }

        Map<Long, List<Comment>> parentMap = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(Comment::getParentId));

        List<Comment> roots = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .collect(Collectors.toList());

        for (Comment c : all) {
            c.setChildren(parentMap.getOrDefault(c.getId(), Collections.emptyList()));
        }

        return roots;
    }

    @Override
    @Transactional
    public boolean likeComment(Long userId, Long commentId) {
        String likeKey = "comment_like:" + commentId + ":" + userId;
        Boolean alreadyLiked = redisTemplate.hasKey(likeKey);
        if (Boolean.TRUE.equals(alreadyLiked)) {
            redisTemplate.delete(likeKey);
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, commentId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
            return false;
        }
        redisTemplate.opsForValue().set(likeKey, "1");
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .eq(Comment::getId, commentId)
                .setSql("like_count = like_count + 1"));
        return true;
    }

    @Override
    public Comment createComment(Long userId, Long postId, Long parentId, String content, String images) {
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
        comment.setImages(images);
        comment.setLikeCount(0);
        comment.setStatus(1);
        commentMapper.insert(comment);

        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, postId).setSql("comment_count = comment_count + 1"));

        // 通知帖子作者有新评论
        Post post = getById(postId);
        if (post != null && !post.getUserId().equals(userId)) {
            notificationService.send(post.getUserId(), "forum_comment",
                    "帖子有新评论", "有人评论了你的帖子「" + post.getTitle() + "」",
                    "{\"postId\":" + postId + "}");
        }

        // 如果是回复评论，通知被回复的评论作者
        if (parentId != null && parentId > 0 && post != null) {
            Comment parentComment = commentMapper.selectById(parentId);
            if (parentComment != null && !parentComment.getUserId().equals(userId)
                    && !parentComment.getUserId().equals(post.getUserId())) {
                notificationService.send(parentComment.getUserId(), "forum_reply",
                        "评论被回复", "有人回复了你在「" + post.getTitle() + "」下的评论",
                        "{\"postId\":" + postId + "}");
            }
        }

        return comment;
    }

    @Override
    public boolean toggleFavorite(Long userId, Long postId) {
        PostFavorite existing = postFavoriteMapper.selectOne(
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .eq(PostFavorite::getPostId, postId));
        if (existing != null) {
            postFavoriteMapper.deleteById(existing.getId());
            return false;
        }
        PostFavorite fav = new PostFavorite();
        fav.setUserId(userId);
        fav.setPostId(postId);
        postFavoriteMapper.insert(fav);
        return true;
    }

    @Override
    public boolean isFavorited(Long userId, Long postId) {
        return postFavoriteMapper.selectCount(
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .eq(PostFavorite::getPostId, postId)) > 0;
    }

    @Override
    public Page<Post> getMyFavorites(Long userId, int page, int size) {
        Page<PostFavorite> favPage = postFavoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .orderByDesc(PostFavorite::getCreateTime));

        Page<Post> result = new Page<>(page, size, favPage.getTotal());
        List<Post> posts = favPage.getRecords().stream()
                .map(f -> getById(f.getPostId()))
                .filter(p -> p != null && p.getStatus() == 1)
                .collect(Collectors.toList());
        result.setRecords(posts);
        return result;
    }

    @Override
    public Page<Post> getMyPosts(Long userId, int page, int size) {
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .eq(Post::getStatus, 1)
                        .orderByDesc(Post::getCreateTime));
    }
}
