package com.campus.forum.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;

public interface ForumService extends IService<Post> {
    Page<Post> listPosts(String category, String keyword, int page, int size);
    Post getPostDetail(Long id);
    Post createPost(Long userId, Post post);
    void likePost(Long userId, Long postId);
    Page<Comment> getComments(Long postId, int page, int size);
    Comment createComment(Long userId, Long postId, Long parentId, String content);
}
