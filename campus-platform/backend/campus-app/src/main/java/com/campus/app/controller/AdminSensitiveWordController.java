package com.campus.app.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.entity.SensitiveWord;
import com.campus.common.mapper.SensitiveWordMapper;
import com.campus.common.moderation.SensitiveWordService;
import com.campus.common.result.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台-自定义敏感词库（仅 ADMIN）：
 * 本地关键词库的增删改查与内容验证接口，发布过滤即按本词库匹配。
 * 词库变更后立即失效内存缓存，下一条发布即按新词库过滤。
 */
@SaCheckRole("ADMIN")
@RestController
@RequestMapping("/api/admin/sensitive-words")
@RequiredArgsConstructor
public class AdminSensitiveWordController {

    private final SensitiveWordMapper mapper;
    private final SensitiveWordService service;
    private final com.campus.common.moderation.ContentFilterService contentFilterService;

    /**
     * 内容验证接口：对文本跑一遍关键词过滤，返回处置动作/命中词/替换结果。
     * 与发布流程共用同一套判定（{@link ContentFilterService#check}），仅用于验证与调词，
     * 发布主流程仍在服务端强制过滤。
     *
     * <p>请求 {@code {"content":"..."}}，响应 action: PASS/REJECT/REPLACE、
     * finalText（level2 替换后文本）、hitWords、needReview（level3 命中）。
     */
    @PostMapping("/check")
    public R<com.campus.common.moderation.ContentFilterService.FilterResult> check(
            @Valid @RequestBody CheckReq req) {
        return R.ok(contentFilterService.check(req.getContent()));
    }

    /** 验证请求体 */
    @Data
    public static class CheckReq {
        /** 待检测文本 */
        @NotBlank(message = "内容不能为空")
        private String content;
    }

    /** 词库列表（可按启用状态/关键词筛选） */
    @GetMapping
    public R<List<SensitiveWord>> list(@RequestParam(required = false) Integer enabled,
                                       @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SensitiveWord> w = new LambdaQueryWrapper<>();
        if (enabled != null) w.eq(SensitiveWord::getEnabled, enabled);
        if (keyword != null && !keyword.isBlank()) w.like(SensitiveWord::getWord, keyword);
        w.orderByDesc(SensitiveWord::getId);
        return R.ok(mapper.selectList(w));
    }

    /** 新增敏感词 */
    @PostMapping
    public R<SensitiveWord> add(@Valid @RequestBody WordReq req) {
        Long exists = mapper.selectCount(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getWord, req.word));
        if (exists != null && exists > 0) {
            return R.fail("该敏感词已存在");
        }
        SensitiveWord w = new SensitiveWord();
        w.setWord(req.word);
        w.setCategory(req.category);
        w.setLevel(req.level);
        w.setEnabled(1);
        mapper.insert(w);
        service.invalidate();
        return R.ok(w);
    }

    /** 更新敏感词（类目/级别/启用状态） */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody WordReq req) {
        SensitiveWord w = mapper.selectById(id);
        if (w == null) return R.fail("敏感词不存在");
        w.setWord(req.word);
        w.setCategory(req.category);
        w.setLevel(req.level);
        if (req.enabled != null) w.setEnabled(req.enabled);
        mapper.updateById(w);
        service.invalidate();
        return R.ok();
    }

    /** 删除敏感词 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        service.invalidate();
        return R.ok();
    }

    /** 敏感词请求 */
    @Data
    public static class WordReq {
        /** 敏感词 */
        @NotBlank(message = "敏感词不能为空")
        public String word;
        /** 类目: 政治/辱骂/色情/广告/谣言/隐私/其他 */
        @NotBlank(message = "类目不能为空")
        public String category;
        /** 处置级别: 1-拒绝发布 2-替换*** 3-送审 */
        @NotNull(message = "级别不能为空")
        @Min(1) @Max(3)
        public Integer level;
        /** 启用状态（更新时可选） */
        public Integer enabled;
    }
}
