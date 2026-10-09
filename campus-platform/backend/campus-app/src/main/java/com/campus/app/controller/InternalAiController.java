package com.campus.app.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.ai.chat.entity.AiChatSession;
import com.campus.ai.chat.mapper.AiChatSessionMapper;
import com.campus.app.dto.AiStatsVO;
import com.campus.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部统计查询接口，当前由 AdminController 直接聚合，保留给未来服务拆分使用。
 * <p>当前无调用方：全库无 Feign，前端与各业务模块均未请求 /internal/**。
 */
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class InternalAiController {

    private final AiChatSessionMapper aiChatSessionMapper;

    /** 获取 AI 模块统计数据 */
    @GetMapping("/stats")
    public R<AiStatsVO> getAiStats() {
        long totalSessions = aiChatSessionMapper.selectCount(null);
        AiStatsVO vo = new AiStatsVO();
        vo.setTotalSessions(totalSessions);
        return R.ok(vo);
    }
}
