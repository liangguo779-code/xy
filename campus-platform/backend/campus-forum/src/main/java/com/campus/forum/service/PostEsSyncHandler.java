package com.campus.forum.service;

import com.campus.common.es.EsSyncHandler;
import com.campus.forum.entity.Post;
import com.campus.forum.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 帖子 ES 同步处理器：把发件箱里的意图落到 {@code posts} 索引。
 *
 * <p>同步时重新读库，仅 status=1（正常可见）的帖子入索引，其余（删除/隐藏/待审核）
 * 一律转为删除文档——与检索侧的回表校验口径一致，避免隐藏帖从索引漏出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostEsSyncHandler implements EsSyncHandler {

    private final PostMapper postMapper;
    private final PostIndexService postIndexService;

    @Override
    public String bizType() {
        return TYPE_POST;
    }

    @Override
    public void syncIndex(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post != null && post.getStatus() != null && post.getStatus() == 1) {
            postIndexService.indexPost(post);
        } else {
            // 已删/隐藏/待审核/行不存在：从索引移除
            postIndexService.deletePost(postId);
        }
    }

    @Override
    public void syncDelete(Long postId) {
        postIndexService.deletePost(postId);
    }
}
