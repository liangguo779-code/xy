package com.campus.ai.retrieval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则同义扩展器：把用户口语用词映射为《学生手册》等知识语料中的正式用词，
 * 生成变体查询供 BM25 多路检索，提升词面匹配召回率。
 *
 * <p>典型场景：短查询（"学分认定"、"挂科重修流程"）跳过 LLM 改写后，
 * 若不做同义扩展，BM25 只能按字面匹配，而手册用词是"重学/不及格/学分转换"等，
 * 词面对不上导致召回失败。
 *
 * <p>实现要点：
 * <ul>
 *   <li>纯规则词表（无 LLM），零延迟、零噪声以外的不可控扩展</li>
 *   <li>按词条整体替换生成变体（如"挂科重修流程"→"不及格重修流程"）</li>
 *   <li>返回列表首元素永远是原查询，变体去重并限量，避免污染 RRF 融合</li>
 * </ul>
 */
@Component
public class SynonymExpander {

    /**
     * 口语/学务俗称 → 手册正式用词（多个同义表述）。
     * 词条按 key 长度降序匹配，保证"学分认定"这类长短语优先于更短词条命中。
     */
    private static final Map<String, List<String>> SYNONYMS = new LinkedHashMap<>();

    static {
        SYNONYMS.put("挂科", List.of("不及格", "补考", "重学"));
        SYNONYMS.put("重修", List.of("重学", "补考", "不及格"));
        SYNONYMS.put("学分认定", List.of("学分转换", "课程替代", "学分互认"));
        SYNONYMS.put("学分转换", List.of("学分认定", "课程替代", "学分互认"));
        SYNONYMS.put("课程替代", List.of("学分认定", "学分转换", "学分互认"));
        SYNONYMS.put("补考", List.of("不及格", "重学"));
        SYNONYMS.put("综测", List.of("综合素质测评", "综合测评"));
        SYNONYMS.put("第二课堂", List.of("课外学分", "素质拓展"));
        SYNONYMS.put("转专业", List.of("专业调整", "转专业申请"));
        SYNONYMS.put("绩点", List.of("学分绩点", "平均学分绩点"));
        SYNONYMS.put("评奖", List.of("奖学金评定", "评优"));
        SYNONYMS.put("助学金", List.of("困难补助", "学生资助"));
        SYNONYMS.put("毕业要求", List.of("毕业资格", "毕业条件", "学位授予"));
        SYNONYMS.put("处分", List.of("纪律处分", "违纪处理"));
        SYNONYMS.put("退学", List.of("学籍异动", "退学处理"));
    }

    /** 变体数量上限（不含原查询），防止长查询命中过多词条时爆炸。 */
    private static final int MAX_VARIANTS = 4;

    /**
     * 对查询做规则同义扩展。
     * 返回列表首元素是原查询本身，其后为按词表替换生成的变体（去重、限量）；
     * 未命中词表时只返回原查询。
     */
    public List<String> expand(String query) {
        List<String> out = new ArrayList<>();
        if (query == null || query.isBlank()) return out;
        String trimmed = query.trim();
        out.add(trimmed);

        Set<String> seen = new LinkedHashSet<>();
        seen.add(trimmed);
        // 每次只替换一个词条（长词条优先），生成"其余部分原样保留"的变体，
        // 保持与原查询的语义贴近，避免组合爆炸。
        List<String> keys = SYNONYMS.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
        for (String key : keys) {
            int idx = trimmed.indexOf(key);
            if (idx < 0) continue;
            String prefix = trimmed.substring(0, idx);
            String suffix = trimmed.substring(idx + key.length());
            for (String syn : SYNONYMS.get(key)) {
                if (out.size() > MAX_VARIANTS) return out;
                String variant = prefix + syn + suffix;
                if (seen.add(variant)) out.add(variant);
            }
        }
        return out;
    }
}
