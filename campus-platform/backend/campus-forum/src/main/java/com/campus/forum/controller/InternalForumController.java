package com.campus.forum.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.result.R;
import com.campus.forum.entity.Post;
import com.campus.feign.forum.dto.PostStatsVO;
import com.campus.forum.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部调用接口（供 admin 服务通过 Feign 调用统计数据）
 */
@RestController
@RequestMapping("/internal/forum")
@RequiredArgsConstructor
public class InternalForumController {

    private final PostMapper postMapper;

    @GetMapping("/stats")
    public R<PostStatsVO> getPostStats() {
        long total = postMapper.selectCount(null);
        long active = postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1));
        PostStatsVO vo = new PostStatsVO();
        vo.setTotal(total);
        vo.setActive(active);
        return R.ok(vo);
    }
}
