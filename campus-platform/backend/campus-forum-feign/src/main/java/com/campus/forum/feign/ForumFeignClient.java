package com.campus.forum.feign;

import com.campus.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "campus-forum-service", path = "/internal/forum")
public interface ForumFeignClient {

    @GetMapping("/stats")
    R<Map<String, Object>> getPostStats();
}
