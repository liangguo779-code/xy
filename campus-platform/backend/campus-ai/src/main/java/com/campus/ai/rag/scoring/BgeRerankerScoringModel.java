package com.campus.ai.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal {@link ScoringModel} that scores text by cosine similarity to the question
 * embedding. This is a placeholder for a proper cross-encoder reranker (e.g.
 * BGE-reranker-base via langchain4j-onnx-scoring). It gives the pipeline a deterministic
 * rerank that preserves the sign-flip semantics used by RagOrchestrator.
 */
@RequiredArgsConstructor
public class BgeRerankerScoringModel implements ScoringModel {

    private final EmbeddingModel embeddingModel;

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return Response.from(Collections.emptyList());
        }
        Embedding queryEmb = embeddingModel.embed(query).content();
        List<Double> out = new ArrayList<>(segments.size());
        for (TextSegment s : segments) {
            Embedding e = embeddingModel.embed(s.text()).content();
            // Negate: higher similarity => more negative value. RagOrchestrator's
            // threshold check (bestScore <= threshold) treats smaller as better.
            out.add(-cosine(queryEmb, e));
        }
        return Response.from(out);
    }

    private static double cosine(Embedding a, Embedding b) {
        float[] x = a.vector();
        float[] y = b.vector();
        int n = Math.min(x.length, y.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            dot += (double) x[i] * y[i];
            na += (double) x[i] * x[i];
            nb += (double) y[i] * y[i];
        }
        if (na == 0 || nb == 0) return 0d;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
