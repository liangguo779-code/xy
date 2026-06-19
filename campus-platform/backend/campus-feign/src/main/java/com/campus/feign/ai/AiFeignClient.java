package com.campus.feign.ai;

import com.campus.common.result.R;
import com.campus.feign.ai.dto.AiStatsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "campus-ai-consult-service", path = "/internal/ai")
public interface AiFeignClient {

    @GetMapping("/stats")
    R<AiStatsVO> getAiStats();
}
