package com.campus.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.ai.entity.AiChatSession;
import com.campus.ai.mapper.AiChatSessionMapper;
import com.campus.feign.ai.dto.AiStatsVO;
import com.campus.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部调用接口（供 admin 服务通过 Feign 调用统计数据）
 */
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class InternalAiController {

    private final AiChatSessionMapper aiChatSessionMapper;

    @GetMapping("/stats")
    public R<AiStatsVO> getAiStats() {
        long totalSessions = aiChatSessionMapper.selectCount(null);
        AiStatsVO vo = new AiStatsVO();
        vo.setTotalSessions(totalSessions);
        return R.ok(vo);
    }
}
