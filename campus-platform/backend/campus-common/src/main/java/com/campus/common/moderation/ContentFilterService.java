package com.campus.common.moderation;

import com.campus.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 发布时内容过滤编排（"机器自动拦截"第一道防线）：纯本地关键词过滤，
 * 基于 sensitive_word 词库（管理后台可维护）做子串匹配，无外部服务依赖——
 * 无内容长度上限、无调用频率限制、无第三方私钥。
 *
 * <p>处置级别（词库 level 字段）：
 * <ol>
 *   <li>level1 命中直接拒绝；</li>
 *   <li>level2 替换 ***；</li>
 *   <li>level3 标记送审（发布但进人审队列）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentFilterService {

    private final SensitiveWordService sensitiveWordService;

    /** 处置动作 */
    public enum Action { PASS, REJECT, REPLACE }

    /**
     * 过滤结果。
     *
     * @param action     处置动作
     * @param finalText  替换后的最终文本（PASS 时为原文）
     * @param hitWords   命中词汇总（写审计流水用）
     * @param needReview 是否需要送人审（custom level3 命中）
     */
    public record FilterResult(Action action, String finalText, List<String> hitWords, boolean needReview) {}

    /** 过滤文本；调用方按 action 决定拒绝/替换/送审（见 {@link #check}）。 */
    public FilterResult filter(String text) {
        return check(text);
    }

    /** 只检测不处置：返回处置动作、替换后文本与命中词（验证接口/发布流程共用同一套判定）。 */
    public FilterResult check(String text) {
        if (text == null || text.isBlank()) {
            return new FilterResult(Action.PASS, text, List.of(), false);
        }
        List<String> allHits = new ArrayList<>();
        boolean needReview = false;

        String current = text;
        for (SensitiveWordService.Hit h : sensitiveWordService.match(text)) {
            allHits.add(h.word() + "(" + h.category() + ")");
            switch (h.level()) {
                case 1 -> {
                    return new FilterResult(Action.REJECT, text, allHits, false);
                }
                case 2 -> current = current.replace(h.word(), "***");
                case 3 -> needReview = true;
                default -> { /* 未知级别按送审处理 */ needReview = true; }
            }
        }

        boolean replaced = !current.equals(text);
        Action action = replaced ? Action.REPLACE : Action.PASS;
        return new FilterResult(action, current, allHits, needReview);
    }
}
