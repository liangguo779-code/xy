package com.campus.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CitationValidator 校验测试：合法引用、越界剥离、引用变体、内容级校验、残留语病清理、拒绝策略边界。 */
class CitationValidatorTest {

    @Test
    void allValidCitations() {
        CitationValidator.Result r = CitationValidator.validate(
                "根据规定[来源1]可以办理[来源2]和[来源3]手续。", 3);
        assertEquals(3, r.totalCitations());
        assertEquals(3, r.validCitations());
        assertEquals(0, r.fabricatedCount());
        assertTrue(r.fabricatedIndices().isEmpty());
        assertEquals("根据规定[来源1]可以办理[来源2]和[来源3]手续。", r.cleanedAnswer());
        assertFalse(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
    }

    @Test
    void stripsOutOfRangeCitation() {
        CitationValidator.Result r = CitationValidator.validate(
                "答案见[来源1]，详细见[来源6]，也参考[来源2]。", 5);
        assertEquals(3, r.totalCitations());
        assertEquals(2, r.validCitations());
        assertEquals(1, r.fabricatedCount());
        assertEquals(List.of(6), r.fabricatedIndices());
        // P2-8：剥离 [来源6] 时其后的悬挂链"，也参考"一并清除（链后是另一个引用标记），
        // 避免残留"详细见，也参考"式语病
        assertEquals("答案见[来源1]，详细见[来源2]。", r.cleanedAnswer());
    }

    @Test
    void stripsZeroAndNegativeAndLarge() {
        CitationValidator.Result r = CitationValidator.validate(
                "[来源0][来源-1][来源100][来源2]", 3);
        assertEquals(4, r.totalCitations());
        assertEquals(1, r.validCitations());
        assertEquals(List.of(0, -1, 100), r.fabricatedIndices());
        // Stripped citations drop the entire bracket (not just the inner text), so
        // consecutive invalid citations collapse without leaving empty [] markers.
        assertEquals("[来源2]", r.cleanedAnswer());
    }

    @Test
    void handlesWhitespaceInsideBrackets() {
        // Valid citations with internal whitespace are accepted as-is; the spaces
        // are harmless and the LLM's surrounding text reads naturally.
        CitationValidator.Result r = CitationValidator.validate(
                "见[来源 1 ]和[来源 2]。", 2);
        assertEquals(2, r.totalCitations());
        assertEquals(2, r.validCitations());
        assertEquals("见[来源 1 ]和[来源 2]。", r.cleanedAnswer());
    }

    @Test
    void stripsAllWhenNoValidSources() {
        CitationValidator.Result r = CitationValidator.validate(
                "答案见[来源1]和[来源2]。", 0);
        assertEquals(2, r.totalCitations());
        assertEquals(0, r.validCitations());
        assertEquals(2, r.fabricatedCount());
        assertEquals("答案见和。", r.cleanedAnswer());
        // shouldReject because totalCitations > 0 AND fabricatedCount/total = 1.0 > 0.5
        assertTrue(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
    }

    @Test
    void noCitationsIsToleratedNotRejected() {
        // P1-10：零引用不再一律拒绝（规范的无引用短答案被误伤代价大），
        // 改由调度器降级处理（追加免责声明 / 降 medium）
        CitationValidator.Result r = CitationValidator.validate(
                "这是一段不带引用的回答。", 5);
        assertEquals(0, r.totalCitations());
        assertEquals(0, r.validCitations());
        assertEquals("这是一段不带引用的回答。", r.cleanedAnswer());
        assertFalse(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
    }

    @Test
    void rejectsWhenMajorityFabricated() {
        CitationValidator.Result r = CitationValidator.validate(
                "[来源1][来源99][来源100][来源101]", 5);
        assertEquals(4, r.totalCitations());
        assertEquals(1, r.validCitations());
        assertEquals(3, r.fabricatedCount());
        // 3/4 = 0.75 > 0.5 ⇒ reject
        assertTrue(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
    }

    @Test
    void doesNotRejectWhenMinorityFabricated() {
        CitationValidator.Result r = CitationValidator.validate(
                "[来源1][来源2][来源3][来源99]", 5);
        assertEquals(4, r.totalCitations());
        assertEquals(3, r.validCitations());
        // 1/4 = 0.25 ≤ 0.5 ⇒ do NOT reject
        assertFalse(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
    }

    @Test
    void nullAnswerIsHandled() {
        CitationValidator.Result r = CitationValidator.validate(null, 5);
        assertEquals("", r.cleanedAnswer());
        assertEquals(0, r.totalCitations());
        // 零引用不拒绝（P1-10），由调用方决定是否降级
        assertFalse(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
    }

    @Test
    void emptyAnswerIsHandled() {
        CitationValidator.Result r = CitationValidator.validate("", 5);
        assertEquals("", r.cleanedAnswer());
        assertEquals(0, r.totalCitations());
        assertFalse(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
    }

    // ── P2-7 引用变体 ────────────────────────────────────────────────────────────

    @Test
    void parsesCitationVariants() {
        // [N]、[来源N]、全角数字 [来源２]、全角括号 ［来源１］ 均识别为引用
        CitationValidator.Result r = CitationValidator.validate(
                "[1]可补考[来源２]，详见[来源 3]，参见［来源１］。年份[2024]不是引用。", 3);
        assertEquals(4, r.totalCitations());
        assertEquals(4, r.validCitations());
        assertEquals(0, r.fabricatedCount());
        // [2024] 纯数字超过 2 位，不当作引用，原样保留
        assertEquals("[1]可补考[来源２]，详见[来源 3]，参见［来源１］。年份[2024]不是引用。",
                r.cleanedAnswer());
    }

    @Test
    void stripsFabricatedVariants() {
        // 变体伪造同样剥离：[来源9] 越界、[９] 全角数字越界
        CitationValidator.Result r = CitationValidator.validate("答案[来源9]和[９]。", 3);
        assertEquals(2, r.totalCitations());
        assertEquals(0, r.validCitations());
        assertEquals(List.of(9, 9), r.fabricatedIndices());
        assertEquals("答案和。", r.cleanedAnswer());
    }

    // ── P1-9 内容级校验 ──────────────────────────────────────────────────────────

    @Test
    void contentMismatchCountsAsFabricated() {
        // 编号合法但内容"张冠李戴"：奖学金结论却引"补考与重学"来源 → 计入伪造
        List<String> contents = List.of(
                "补考与重学：不及格课程可在下学期开学初申请补考，补考不通过需申请重学。",
                "奖学金评定：一等奖 2000 元，按学年评定。");
        CitationValidator.Result r = CitationValidator.validate(
                "补考不通过需申请重学[来源1]。一等奖 2000 元按学年评定[来源1]。", contents);
        assertEquals(2, r.totalCitations());
        assertEquals(1, r.validCitations());
        assertEquals(1, r.fabricatedCount());
        assertEquals(List.of(1), r.fabricatedIndices());
        assertEquals("补考不通过需申请重学[来源1]。一等奖 2000 元按学年评定。", r.cleanedAnswer());
    }

    @Test
    void contentMatchedCitationIsKept() {
        List<String> contents = List.of(
                "补考与重学：不及格课程可在下学期开学初申请补考，补考不通过需申请重学。",
                "奖学金评定：一等奖 2000 元，按学年评定。");
        CitationValidator.Result r = CitationValidator.validate(
                "补考不通过需申请重学[来源1]。一等奖 2000 元按学年评定[来源2]。", contents);
        assertEquals(2, r.totalCitations());
        assertEquals(2, r.validCitations());
        assertEquals(0, r.fabricatedCount());
        assertEquals("补考不通过需申请重学[来源1]。一等奖 2000 元按学年评定[来源2]。", r.cleanedAnswer());
    }

    @Test
    void contentMismatchInflatesRejectRatio() {
        // 4 个引用全部"合法编号 + 错误内容" → 伪造占比 1.0 → 整体拒绝
        List<String> contents = List.of("补考与重学：不及格课程可申请补考。");
        CitationValidator.Result r = CitationValidator.validate(
                "奖学金分三档[来源1]，另见[来源1]，详见[来源1]，参考[来源1]。", contents);
        assertEquals(4, r.totalCitations());
        assertEquals(0, r.validCitations());
        assertTrue(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
    }

    // ── P2-8 剥离后残留语病清理 ──────────────────────────────────────────────────

    @Test
    void danglingConnectorChainIsCleaned() {
        // 两处伪造全剥离时，"详见…，也参考…"连接词链不能留在句子里
        CitationValidator.Result r = CitationValidator.validate(
                "具体规定详见[来源3]，也参考[来源4]。", 2);
        assertEquals(2, r.totalCitations());
        assertEquals(0, r.validCitations());
        assertEquals("具体规定。", r.cleanedAnswer());
    }

    @Test
    void danglingChainBeforeRealTextIsKept() {
        // 悬挂链后是正常正文（"下文"）时不能整段吃掉，只剥离标记与前导连接词
        CitationValidator.Result r = CitationValidator.validate(
                "详见[来源3]，也参考下文。", 2);
        assertEquals(1, r.totalCitations());
        assertEquals("也参考下文。", r.cleanedAnswer());
    }

    @Test
    void parenthesizedCitationIsCleaned() {
        // "（[来源9]）" 剥离后不留空括号
        CitationValidator.Result r = CitationValidator.validate(
                "补考规定（[来源9]）见正文。", 2);
        assertEquals(1, r.totalCitations());
        assertEquals("补考规定见正文。", r.cleanedAnswer());
    }

    @Test
    void lonelyPunctuationIsDropped() {
        // 整句只剩"详见[来源9]。"时剥离后不留孤立句号
        CitationValidator.Result r = CitationValidator.validate("详见[来源9]。", 2);
        assertEquals(1, r.totalCitations());
        assertEquals("", r.cleanedAnswer());
    }

    // ── 引用格式兼容：六角括号/圆括号/裸形式 ─────────────────────────────────────

    @Test
    void parsesBracketStyleVariants() {
        // 【来源1】六角括号、（来源2）全角圆括号、(来源3) 半角圆括号 均识别为引用
        CitationValidator.Result r = CitationValidator.validate(
                "休学需申请【来源1】，经审批（来源2），详见(来源3)。", 3);
        assertEquals(3, r.totalCitations());
        assertEquals(3, r.validCitations());
        assertEquals(0, r.fabricatedCount());
        assertEquals("休学需申请【来源1】，经审批（来源2），详见(来源3)。", r.cleanedAnswer());
    }

    @Test
    void parsesBareCitationAtSentenceBoundary() {
        // 裸形式"来源3"仅当前后是标点/空白/句边界时识别
        CitationValidator.Result r = CitationValidator.validate(
                "补考可申请。来源3 有详细规定。", 3);
        assertEquals(1, r.totalCitations());
        assertEquals(1, r.validCitations());
        assertEquals("补考可申请。来源3 有详细规定。", r.cleanedAnswer());
    }

    @Test
    void bareOutOfRangeCitationIsStripped() {
        // 裸形式越界同样剥离（连同前导悬挂连接词"详见"），不残留言病
        CitationValidator.Result r = CitationValidator.validate("详见 来源9。", 3);
        assertEquals(1, r.totalCitations());
        assertEquals(0, r.validCitations());
        assertEquals(List.of(9), r.fabricatedIndices());
        assertEquals("", r.cleanedAnswer());
    }

    @Test
    void doesNotMatchYearOrNonCitationBrackets() {
        // [2024] 年份（纯数字超 2 位）、【置信度提示】（"来源"后未跟数字）均不误匹配
        CitationValidator.Result r = CitationValidator.validate(
                "年份[2024]不是引用。【置信度提示】仅供参考。正确的是[来源2]。", 3);
        assertEquals(1, r.totalCitations());
        assertEquals(1, r.validCitations());
        assertEquals("年份[2024]不是引用。【置信度提示】仅供参考。正确的是[来源2]。",
                r.cleanedAnswer());
    }

    @Test
    void bareNarrativeIsNotCountedAsCitation() {
        // 正文叙述"来源1是错误的"（后接字母）不是引用；同句的 [来源2] 正常计数
        CitationValidator.Result r = CitationValidator.validate(
                "来源1是错误的，正确的是[来源2]。", 3);
        assertEquals(1, r.totalCitations());
        assertEquals(1, r.validCitations());
        assertEquals("来源1是错误的，正确的是[来源2]。", r.cleanedAnswer());
    }

    // ── 零引用自动补注（系统补注） ──────────────────────────────────────────────

    @Test
    void autoCiteInjectsMatchingSentence() {
        // 零引用回答按句匹配来源后在句末注入正确编号（句读之前）
        List<String> contents = List.of("补考与重学：不及格课程可在下学期开学初申请补考。");
        CitationValidator.AutoCite ac = CitationValidator.autoCite("补考可在开学初申请。", contents);
        assertEquals("补考可在开学初申请[来源1]。", ac.annotatedAnswer());
        assertEquals(1, ac.injectedSentences());
        assertEquals(1, ac.substantiveSentences());
        assertEquals(1.0d, ac.coverage());
    }

    @Test
    void autoCitePicksMostSimilarSourceNotFirst() {
        // 句子讲奖学金，与来源2 相似：不得张冠李戴标成来源1
        List<String> contents = List.of(
                "补考与重学：不及格课程可在下学期开学初申请补考。",
                "奖学金评定：一等奖 2000 元，按学年评定。");
        CitationValidator.AutoCite ac = CitationValidator.autoCite("一等奖 2000 元按学年评定。", contents);
        assertEquals("一等奖 2000 元按学年评定[来源2]。", ac.annotatedAnswer());
    }

    @Test
    void autoCiteSkipsUnrelatedSentence() {
        // 伪造注入防线：无关句子不得强塞编号，覆盖率按实质句分母计算
        List<String> contents = List.of("补考与重学：不及格课程可在下学期开学初申请补考。");
        CitationValidator.AutoCite ac = CitationValidator.autoCite(
                "补考可在开学初申请。今天天气不错。", contents);
        assertEquals("补考可在开学初申请[来源1]。今天天气不错。", ac.annotatedAnswer());
        assertEquals(1, ac.injectedSentences());
        assertEquals(2, ac.substantiveSentences());
    }

    @Test
    void autoCiteLeavesUnrelatedAnswerUntouched() {
        // 完全无关的回答不注入，原文原样返回
        List<String> contents = List.of("补考与重学：不及格课程可在下学期开学初申请补考。");
        CitationValidator.AutoCite ac = CitationValidator.autoCite(
                "这个问题我无法确定，建议咨询教务处。", contents);
        assertEquals("这个问题我无法确定，建议咨询教务处。", ac.annotatedAnswer());
        assertEquals(0, ac.injectedSentences());
    }

    @Test
    void autoCiteIgnoresGreetingOnlySentences() {
        // 寒暄句（去噪后过短）不参与匹配，也不会被强塞编号，且不计入覆盖率分母
        List<String> contents = List.of("补考与重学：不及格课程可在下学期开学初申请补考。");
        CitationValidator.AutoCite ac = CitationValidator.autoCite("同学你好！补考可在开学初申请。", contents);
        assertEquals("同学你好！补考可在开学初申请[来源1]。", ac.annotatedAnswer());
        assertEquals(1, ac.injectedSentences());
        assertEquals(1, ac.substantiveSentences());
    }

    @Test
    void autoCiteInjectedMarkersPassReparse() {
        // 注入后的标记可被正常解析（复跑解析校验 total>0）
        List<String> contents = List.of(
                "补考与重学：不及格课程可在下学期开学初申请补考。",
                "奖学金评定：一等奖 2000 元，按学年评定。");
        CitationValidator.AutoCite ac = CitationValidator.autoCite(
                "补考可在开学初申请。一等奖 2000 元按学年评定。", contents);
        CitationValidator.Result r = CitationValidator.validate(ac.annotatedAnswer(), 2);
        assertEquals(2, r.totalCitations());
        assertEquals(2, r.validCitations());
        assertEquals(ac.annotatedAnswer(), r.cleanedAnswer());
    }
}
