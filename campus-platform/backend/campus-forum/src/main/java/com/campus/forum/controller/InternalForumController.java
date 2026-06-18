package com.campus.forum.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.result.R;
import com.campus.forum.entity.Post;
import com.campus.forum.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部调用接口（供 admin 服务通过 Feign 调用统计数据）
 */
@RestController
@RequestMapping("/internal/forum")
@RequiredArgsConstructor
public class InternalForumController {

    private final PostMapper postMapper;

    @GetMapping("/stats")
    public R<Map<String, Object>> getPostStats() {
        long total = postMapper.selectCount(null);
        long active = postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1));
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("active", active);
        return R.ok(stats);
    }
}
