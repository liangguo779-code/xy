package com.campus.ai.rag;

import com.campus.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfidenceGraderTest {

    private AiProperties propsWith(double high, double mid) {
        AiProperties p = new AiProperties();
        p.getKnowledge().setHighConfidenceThreshold(high);
        p.getKnowledge().setMediumConfidenceThreshold(mid);
        return p;
    }

    @Test
    void highConfidenceAtUpperBoundary() {
        // Default thresholds: high=-0.6, medium=-0.3
        assertEquals(ConfidenceGrader.HIGH, ConfidenceGrader.grade(-0.6,
                propsWith(-0.6, -0.3)));
    }

    @Test
    void highConfidenceAboveUpperBoundary() {
        assertEquals(ConfidenceGrader.HIGH, ConfidenceGrader.grade(-0.7,
                propsWith(-0.6, -0.3)));
        assertEquals(ConfidenceGrader.HIGH, ConfidenceGrader.grade(-1.0,
                propsWith(-0.6, -0.3)));
    }

    @Test
    void mediumConfidenceBetweenBoundaries() {
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(-0.5,
                propsWith(-0.6, -0.3)));
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(-0.4,
                propsWith(-0.6, -0.3)));
    }

    @Test
    void mediumConfidenceAtLowerBoundary() {
        // Inclusive boundary: -0.3 still counts as medium
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(-0.3,
                propsWith(-0.6, -0.3)));
    }

    @Test
    void lowConfidenceAboveLowerBoundary() {
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(-0.29,
                propsWith(-0.6, -0.3)));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(-0.1,
                propsWith(-0.6, -0.3)));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(0.0,
                propsWith(-0.6, -0.3)));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(0.5,
                propsWith(-0.6, -0.3)));
    }

    @Test
    void noResultSentinelIsAlwaysLow() {
        assertEquals(ConfidenceGrader.LOW,
                ConfidenceGrader.grade(ConfidenceGrader.NO_RESULT, new AiProperties()));
    }

    @Test
    void customThresholdsAreRespected() {
        // Tight high bar: only very good matches count
        AiProperties p = propsWith(-0.8, -0.5);
        assertEquals(ConfidenceGrader.HIGH, ConfidenceGrader.grade(-0.9, p));
        assertEquals(ConfidenceGrader.MEDIUM, ConfidenceGrader.grade(-0.6, p));
        assertEquals(ConfidenceGrader.LOW, ConfidenceGrader.grade(-0.3, p));
    }
}
