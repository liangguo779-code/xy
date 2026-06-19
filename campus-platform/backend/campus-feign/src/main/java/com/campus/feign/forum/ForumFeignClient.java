package com.campus.feign.forum;

import com.campus.common.result.R;
import com.campus.feign.forum.dto.PostStatsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "campus-forum-service", path = "/internal/forum")
public interface ForumFeignClient {

    @GetMapping("/stats")
    R<PostStatsVO> getPostStats();
}
