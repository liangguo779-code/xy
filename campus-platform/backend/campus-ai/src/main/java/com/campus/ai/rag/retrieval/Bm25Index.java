package com.campus.ai.rag.retrieval;

import com.campus.ai.knowledge.model.ChunkDto;
import com.huaban.analysis.jieba.JiebaSegmenter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory BM25 index over chunked knowledge documents, with a jieba tokenizer for
 * Chinese-friendly tokenization. This is a port of the Python service's
 * {@code rag.bm25.BM25Okapi} usage. Persistence is best-effort: a JSON snapshot of the
 * tokenized corpus is written under {@code campus.ai.home/vectors/bm25.json} and reloaded
 * on startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Bm25Index {

    private static final double K1 = 1.2d;
    private static final double B = 0.75d;

    private final com.campus.ai.config.AiProperties props;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<Doc> docs = new ArrayList<>();
    private List<List<String>> docTokens = new ArrayList<>();
    private Map<String, Integer> df = new HashMap<>();
    private double avgDocLen = 0d;
    private int totalDocs = 0;

    private final transient JiebaSegmenter segmenter = new JiebaSegmenter();

    @PostConstruct
    void loadOrBuild() {
        Path snapshot = snapshotPath();
        if (Files.exists(snapshot)) {
            try {
                load(snapshot);
                log.info("BM25 index loaded from {} ({} docs)", snapshot, totalDocs);
                return;
            } catch (Exception e) {
                log.warn("Failed to load BM25 snapshot, will rebuild from vector store: {}", e.getMessage());
            }
        }
        // No snapshot yet: leave empty. The KnowledgeService rebuild flow will populate.
    }

    /** Replace the entire index with the given chunks. */
    public void replaceAll(List<ChunkDto> chunks) {
        lock.writeLock().lock();
        try {
            docs = new ArrayList<>(chunks.size());
            docTokens = new ArrayList<>(chunks.size());
            df = new HashMap<>();
            for (ChunkDto c : chunks) {
                List<String> tokens = tokenize(c.getContent());
                docs.add(new Doc(c.getSource(), c.getChunkIndex(), c.getContent(), c.isParent()));
                docTokens.add(tokens);
                for (String t : new HashSet<>(tokens)) {
                    df.merge(t, 1, Integer::sum);
                }
            }
            totalDocs = docs.size();
            avgDocLen = totalDocs == 0 ? 0 : docTokens.stream().mapToInt(List::size).average().orElse(0);
            persist();
            log.info("BM25 index rebuilt: {} docs, avgDocLen={}", totalDocs, avgDocLen);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addAll(List<ChunkDto> chunks) {
        // Rebuild from scratch — small corpus, deterministic and simple. The Python service
        // also rebuilds the full index on every add.
        replaceAll(currentChunksPlus(chunks));
    }

    private List<ChunkDto> currentChunksPlus(List<ChunkDto> added) {
        List<ChunkDto> all = new ArrayList<>(docs.size() + added.size());
        for (int i = 0; i < docs.size(); i++) {
            Doc d = docs.get(i);
            all.add(ChunkDto.builder()
                    .source(d.source)
                    .chunkIndex(d.chunkIndex)
                    .content(d.content)
                    .isParent(d.isParent)
                    .build());
        }
        all.addAll(added);
        return all;
    }

    public void removeBySource(String source) {
        lock.writeLock().lock();
        try {
            List<Doc> nd = new ArrayList<>();
            List<List<String>> nt = new ArrayList<>();
            for (int i = 0; i < docs.size(); i++) {
                if (!docs.get(i).source.equals(source)) {
                    nd.add(docs.get(i));
                    nt.add(docTokens.get(i));
                }
            }
            docs = nd;
            docTokens = nt;
            totalDocs = docs.size();
            avgDocLen = totalDocs == 0 ? 0 : docTokens.stream().mapToInt(List::size).average().orElse(0);
            // Recompute df.
            df = new HashMap<>();
            for (List<String> t : docTokens) {
                for (String term : new HashSet<>(t)) {
                    df.merge(term, 1, Integer::sum);
                }
            }
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Hit> search(String query, int topK, Set<String> disabledSources) {
        lock.readLock().lock();
        try {
            if (docs.isEmpty()) return List.of();
            List<String> qTokens = tokenize(query);
            if (qTokens.isEmpty()) return List.of();

            // Score each doc, filtering out parent chunks (only use children for retrieval).
            List<Scored> scored = new ArrayList<>();
            for (int i = 0; i < docs.size(); i++) {
                Doc d = docs.get(i);
                if (d.isParent) continue;
                if (disabledSources != null && disabledSources.contains(d.source)) continue;
                double s = bm25Score(qTokens, docTokens.get(i));
                if (s > 0) scored.add(new Scored(i, s));
            }
            scored.sort((a, b) -> Double.compare(b.score, a.score));
            List<Hit> out = new ArrayList<>();
            for (int i = 0; i < Math.min(topK, scored.size()); i++) {
                Scored sc = scored.get(i);
                Doc d = docs.get(sc.idx);
                out.add(new Hit(d.source, d.chunkIndex, d.content, sc.score));
            }
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        return totalDocs;
    }

    private double bm25Score(List<String> q, List<String> d) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : d) tf.merge(t, 1, Integer::sum);
        double score = 0;
        int docLen = d.size();
        for (String term : q) {
            int f = tf.getOrDefault(term, 0);
            if (f == 0) continue;
            int n = df.getOrDefault(term, 0);
            double idf = Math.log(1 + (totalDocs - n + 0.5d) / (n + 0.5d));
            double norm = f * (K1 + 1) / (f + K1 * (1 - B + B * docLen / Math.max(1, avgDocLen)));
            score += idf * norm;
        }
        return score;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return List.of();
        // jieba's process returns Iterable<SegToken> — extract the .word field.
        List<String> out = new ArrayList<>();
        for (com.huaban.analysis.jieba.SegToken t : segmenter.process(text, JiebaSegmenter.SegMode.SEARCH)) {
            String s = t.word == null ? "" : t.word.trim().toLowerCase(Locale.ROOT);
            if (!s.isEmpty() && s.length() > 1) out.add(s);
        }
        return out;
    }

    private Path snapshotPath() {
        return Path.of(props.getHome(), "vectors", "bm25.json");
    }

    private void persist() {
        Path p = snapshotPath();
        try {
            Files.createDirectories(p.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("{\"docs\":[");
            for (int i = 0; i < docs.size(); i++) {
                if (i > 0) sb.append(",");
                Doc d = docs.get(i);
                sb.append("{\"source\":\"").append(escape(d.source))
                        .append("\",\"chunkIndex\":").append(d.chunkIndex)
                        .append(",\"content\":\"").append(escape(d.content))
                        .append("\",\"isParent\":").append(d.isParent).append("}");
            }
            sb.append("]}");
            Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to persist BM25 snapshot: {}", e.getMessage());
        }
    }

    private void load(Path p) throws IOException {
        // Use Jackson ObjectMapper for robust JSON parsing instead of regex,
        // which fails when content contains escaped quotes or special characters.
        String s = Files.readString(p, StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<Doc> nd = new ArrayList<>();
        try {
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(s);
            com.fasterxml.jackson.databind.JsonNode docsNode = root.get("docs");
            if (docsNode != null && docsNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : docsNode) {
                    String source = node.has("source") ? node.get("source").asText() : "";
                    int chunkIndex = node.has("chunkIndex") ? node.get("chunkIndex").asInt() : 0;
                    String content = node.has("content") ? node.get("content").asText() : "";
                    boolean isParent = node.has("isParent") && node.get("isParent").asBoolean();
                    if (!content.isEmpty()) {
                        nd.add(new Doc(source, chunkIndex, content, isParent));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Jackson parse failed, falling back to regex: {}", e.getMessage());
            // Fallback to regex for backward compatibility.
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "\\{\"source\":\"(.*?)\",\"chunkIndex\":(\\d+),\"content\":\"(.*?)\"\\}").matcher(s);
            while (m.find()) {
                nd.add(new Doc(unescape(m.group(1)), Integer.parseInt(m.group(2)), unescape(m.group(3)), false));
            }
        }
        docs = nd;
        docTokens = new ArrayList<>(docs.size());
        df = new HashMap<>();
        for (Doc d : docs) {
            List<String> t = tokenize(d.content);
            docTokens.add(t);
            for (String term : new HashSet<>(t)) {
                df.merge(term, 1, Integer::sum);
            }
        }
        totalDocs = docs.size();
        avgDocLen = totalDocs == 0 ? 0 : docTokens.stream().mapToInt(List::size).average().orElse(0);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public record Hit(String source, int chunkIndex, String content, double score) {}
    private record Doc(String source, int chunkIndex, String content, boolean isParent) {}
    private record Scored(int idx, double score) {}
}
