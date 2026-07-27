package com.campus.ai.rag.retrieval;

import com.campus.ai.knowledge.model.ChunkDto;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Thin wrapper over LangChain4j's {@link EmbeddingStore} for the campus knowledge index.
 * Stores one document per chunk; metadata carries {@code source}, {@code chunk_index},
 * {@code section_title}, {@code section_path}. Disabled files are filtered out at query
 * time by passing a {@code source NOT IN} filter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreFacade {

    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel embeddingModel;

    public void addAll(List<ChunkDto> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        List<TextSegment> segments = new ArrayList<>(chunks.size());
        List<Embedding> embeddings = new ArrayList<>(chunks.size());
        for (ChunkDto c : chunks) {
            Metadata meta = new Metadata()
                    .put("source", c.getSource())
                    .put("chunk_index", c.getChunkIndex())
                    .put("section_title", c.getSectionTitle() == null ? "" : c.getSectionTitle())
                    .put("section_path", c.getSectionPath() == null ? "" : c.getSectionPath());
            TextSegment seg = TextSegment.from(c.getContent(), meta);
            segments.add(seg);
            embeddings.add(embeddingModel.embed(seg).content());
        }
        store.addAll(embeddings, segments);
        log.info("Vector store ingested {} chunks", chunks.size());
    }

    public void removeBySource(String source) {
        try {
            store.removeAll(new dev.langchain4j.store.embedding.filter.comparison.IsEqualTo("source", source));
            log.info("Vector store removed all chunks for source={}", source);
        } catch (Exception e) {
            log.warn("Vector store removeBySource failed for {}: {}", source, e.getMessage());
        }
    }

    public List<Hit> search(String query, int topK, Set<String> disabledSources) {
        Embedding queryEmb = embeddingModel.embed(query).content();
        Filter filter = null;
        if (disabledSources != null && !disabledSources.isEmpty()) {
            filter = new IsNotIn("source", disabledSources);
        }
        EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)
                .maxResults(topK)
                .filter(filter)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(req);
        List<Hit> out = new ArrayList<>();
        for (var match : result.matches()) {
            TextSegment seg = match.embedded();
            Metadata m = seg.metadata();
            Integer chunkIndex = m.getInteger("chunk_index");
            out.add(new Hit(
                    m.getString("source"),
                    chunkIndex == null ? 0 : chunkIndex,
                    seg.text(),
                    match.score()
            ));
        }
        return out;
    }

    public int count() {
        try {
            // langchain4j-elasticsearch doesn't expose a count helper through the SPI; callers
            // generally want this for health checks, where 0/-1 is acceptable.
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public record Hit(String source, int chunkIndex, String content, double score) {}
}
