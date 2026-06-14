package com.campus.forum.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;

import java.util.List;

public interface ForumService extends IService<Post> {
    Page<Post> listPosts(String category, String keyword, int page, int size);
    Post getPostDetail(Long id, Long userId);
    Post createPost(Long userId, Post post);
    Post updatePost(Long userId, Long id, Post post);
    void deletePost(Long userId, Long id);
    boolean likePost(Long userId, Long postId);
    Page<Comment> getComments(Long postId, int page, int size);
    List<Comment> getCommentTree(Long postId, Long userId);
    Comment createComment(Long userId, Long postId, Long parentId, String content, String images);
    boolean likeComment(Long userId, Long commentId);
    boolean toggleFavorite(Long userId, Long postId);
    boolean isFavorited(Long userId, Long postId);
    Page<Post> getMyFavorites(Long userId, int page, int size);
    Page<Post> getMyPosts(Long userId, int page, int size);
}
