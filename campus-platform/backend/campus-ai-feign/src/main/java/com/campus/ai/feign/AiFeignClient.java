package com.campus.ai.feign;

import com.campus.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "campus-ai-consult-service", path = "/internal/ai")
public interface AiFeignClient {

    @GetMapping("/stats")
    R<Map<String, Object>> getAiStats();
}
