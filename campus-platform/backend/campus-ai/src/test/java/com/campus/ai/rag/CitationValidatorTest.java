package com.campus.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("答案见[来源1]，详细见，也参考[来源2]。", r.cleanedAnswer());
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
    void noCitationsAtAllIsTreatedAsRejection() {
        // LLM didn't cite anything — either fully hallucinated or answered without
        // sources. Either way, the orchestrator shouldn't trust the answer alone.
        CitationValidator.Result r = CitationValidator.validate(
                "这是一段不带引用的回答。", 5);
        assertEquals(0, r.totalCitations());
        assertEquals(0, r.validCitations());
        assertEquals("这是一段不带引用的回答。", r.cleanedAnswer());
        assertTrue(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
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
        assertTrue(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
    }

    @Test
    void emptyAnswerIsHandled() {
        CitationValidator.Result r = CitationValidator.validate("", 5);
        assertEquals("", r.cleanedAnswer());
        assertEquals(0, r.totalCitations());
        assertTrue(CitationValidator.shouldReject(r.totalCitations(), r.fabricatedCount()));
    }
}
