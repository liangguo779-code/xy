package com.campus.app.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.result.R;
import com.campus.forum.entity.Post;
import com.campus.app.dto.PostStatsVO;
import com.campus.forum.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部统计查询接口，当前由 AdminController 直接聚合，保留给未来服务拆分使用。
 * <p>当前无调用方：全库无 Feign，前端与各业务模块均未请求 /internal/**。
 */
@RestController
@RequestMapping("/internal/forum")
@RequiredArgsConstructor
public class InternalForumController {

    private final PostMapper postMapper;

    /** 获取论坛帖子统计数据 */
    @GetMapping("/stats")
    public R<PostStatsVO> getPostStats() {
        long total = postMapper.selectCount(null);
        long active = postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1)); // status=1 正常展示中
        PostStatsVO vo = new PostStatsVO();
        vo.setTotal(total);
        vo.setActive(active);
        return R.ok(vo);
    }
}
