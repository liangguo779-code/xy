package com.campus.ai.knowledge;

import com.campus.ai.config.AiProperties;
import com.campus.ai.config.RebuildStatusRegistry;
import com.campus.ai.knowledge.model.ChunkDto;
import com.campus.ai.knowledge.pipeline.DocumentConverter;
import com.campus.ai.knowledge.pipeline.IngestionQueue;
import com.campus.ai.knowledge.pipeline.MarkdownSectionSplitter;
import com.campus.ai.rag.retrieval.Bm25Index;
import com.campus.ai.knowledge.store.DisabledFilesCache;
import com.campus.ai.rag.retrieval.VectorStoreFacade;
import com.campus.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

/**
 * Knowledge base lifecycle: scan, upload, toggle, delete, rebuild. Mirrors the
 * {@code api/knowledge.py} FastAPI router with the same JSON shapes consumed by
 * {@code frontend/src/views/admin/KnowledgeManage.vue}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final AiProperties props;
    private final DocumentConverter converter;
    private final MarkdownSectionSplitter splitter;
    private final VectorStoreFacade vectorStore;
    private final Bm25Index bm25;
    private final DisabledFilesCache disabled;
    private final RebuildStatusRegistry rebuildStatus;
    private final IngestionQueue ingestionQueue;

    @PostConstruct
    void init() throws IOException {
        Path dir = knowledgeDir();
        Files.createDirectories(dir);
        seedIfEmpty(dir);
    }

    public Map<String, Object> list() {
        Path dir = knowledgeDir();
        Set<String> disabledSet = this.disabled.getDisabled();
        List<Map<String, Object>> files = new ArrayList<>();
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(KnowledgeService::isAllowedFile)
                        .forEach(p -> files.add(toFileEntry(p, disabledSet)));
            } catch (IOException e) {
                throw new BusinessException(500, "扫描知识库失败: " + e.getMessage());
            }
        }
        files.sort(Comparator.comparing(m -> m.get("name").toString()));
        return Map.of("files", files);
    }

    public Map<String, Object> upload(MultipartFile file) {
        validateSize(file);
        String original = Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin");
        String safeName = Paths.get(original).getFileName().toString();
        validateSuffix(safeName);
        Path target = knowledgeDir().resolve(safeName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(500, "保存文件失败: " + e.getMessage());
        }
        ingestionQueue.submit(() -> ingestOne(target, safeName));
        return Map.of("message", "已提交后台入库", "status", "processing", "name", safeName);
    }

    public Map<String, Object> getContent(String filename) {
        Path file = knowledgeDir().resolve(filename).normalize();
        ensureInsideKnowledge(file);
        if (!Files.exists(file)) {
            throw new BusinessException(404, "文件不存在");
        }
        try {
            String content = converter.toMarkdown(file);
            return Map.of("filename", filename, "content", content);
        } catch (IOException e) {
            throw new BusinessException(500, "读取文件失败: " + e.getMessage());
        }
    }

    public Map<String, Object> update(String filename, String content) {
        Path file = knowledgeDir().resolve(filename).normalize();
        ensureInsideKnowledge(file);
        validateSuffix(filename);
        if (!Files.exists(file)) {
            throw new BusinessException(404, "文件不存在");
        }
        try {
            Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(500, "写入文件失败: " + e.getMessage());
        }
        ingestOne(file, filename);
        return Map.of("message", "已更新并重新入库", "filename", filename);
    }

    public void delete(String filename) {
        Path file = knowledgeDir().resolve(filename).normalize();
        ensureInsideKnowledge(file);
        if (!Files.exists(file)) {
            throw new BusinessException(404, "文件不存在");
        }
        try {
            vectorStore.removeBySource(filename);
            bm25.removeBySource(filename);
            Files.delete(file);
        } catch (IOException e) {
            throw new BusinessException(500, "删除文件失败: " + e.getMessage());
        }
    }

    public Map<String, Object> toggle(String filename) {
        boolean enabled = disabled.toggle(filename);
        return Map.of("message", enabled ? "已启用" : "已禁用", "enabled", enabled, "name", filename);
    }

    public Map<String, Object> rebuild() {
        rebuildStatus.reset();
        ingestionQueue.submit(this::rebuildAll);
        return Map.of("message", "已启动重建", "status", "processing");
    }

    public Map<String, Object> rebuildStatus() {
        return rebuildStatus.snapshot();
    }

    private void rebuildAll() {
        Path dir = knowledgeDir();
        List<Path> files = new ArrayList<>();
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(KnowledgeService::isAllowedFile)
                        .forEach(files::add);
            } catch (IOException e) {
                log.warn("Failed to walk knowledge dir: {}", e.getMessage());
            }
        }
        int total = files.size();
        int totalChunks = 0;
        int done = 0;
        for (Path f : files) {
            try {
                totalChunks += ingestOne(f, f.getFileName().toString());
            } catch (Exception e) {
                log.warn("Failed to ingest {}: {}", f, e.getMessage());
            }
            done++;
            rebuildStatus.updateProgress(total == 0 ? 100 : (int) (done * 100.0 / total));
        }
        rebuildStatus.complete(total, totalChunks);
    }

    private static boolean isAllowedFile(Path p) {
        String name = p.getFileName().toString();
        if (name.startsWith("_") || name.startsWith(".")) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".txt")
                || lower.endsWith(".pdf") || lower.endsWith(".docx")
                || lower.endsWith(".doc");
    }

    private Map<String, Object> toFileEntry(Path p, Set<String> disabledSet) {
        Map<String, Object> f = new LinkedHashMap<>();
        String name = p.getFileName().toString();
        f.put("name", name);
        try {
            f.put("size", Files.size(p));
        } catch (IOException e) {
            f.put("size", 0);
        }
        f.put("suffix", suffixOf(name));
        f.put("enabled", !disabledSet.contains(name));
        return f;
    }

    private int ingestOne(Path file, String name) {
        try {
            String markdown = converter.toMarkdown(file);
            List<ChunkDto> chunks = splitter.split(name, markdown,
                    props.getKnowledge().getChunkSize(),
                    props.getKnowledge().getChunkOverlap());
            // 1. Add new chunks first — if bulk fails, old data stays untouched.
            vectorStore.addAll(chunks);
            // 2. Now remove old chunks that have different IDs from the new ones.
            Set<String> newIds = chunks.stream()
                    .map(c -> c.getSource() + "_" + c.getChunkIndex())
                    .collect(java.util.stream.Collectors.toSet());
            vectorStore.removeStaleBySource(name, newIds);
            bm25.addAll(chunks);
            log.info("Ingested {} chunks from {}", chunks.size(), name);
            return chunks.size();
        } catch (Exception e) {
            log.warn("Failed to ingest {}: {}", name, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void seedIfEmpty(Path dir) {
        boolean empty;
        try (Stream<Path> stream = Files.list(dir)) {
            empty = stream.findAny().isEmpty();
        } catch (IOException e) {
            return;
        }
        if (!empty) return;
        // Best-effort: copy bundled classpath:knowledge/* into the runtime dir.
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:knowledge/*");
            for (Resource r : resources) {
                if (!r.isReadable()) continue;
                String fname = r.getFilename();
                if (fname == null) continue;
                Path target = dir.resolve(fname);
                try (var in = r.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            log.info("No bundled classpath:knowledge seed found ({}); starting with empty KB", e.getMessage());
        }
    }

    private Path knowledgeDir() {
        String dir = props.getKnowledge().getDir();
        if (dir == null || dir.isBlank()) {
            dir = Paths.get(props.getHome(), "knowledge").toString();
        }
        return Paths.get(dir);
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > props.getKnowledge().getMaxFileSize().toBytes()) {
            throw new BusinessException(400, "文件超过大小限制");
        }
    }

    private void validateSuffix(String filename) {
        String suffix = suffixOf(filename);
        Set<String> allowed = new HashSet<>();
        for (String s : props.getKnowledge().getAllowedSuffixes().split(",")) {
            allowed.add(s.trim().toLowerCase(java.util.Locale.ROOT));
        }
        if (!allowed.contains(suffix.toLowerCase(java.util.Locale.ROOT))) {
            throw new BusinessException(400, "不允许的文件类型: " + suffix);
        }
    }

    private static String suffixOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private void ensureInsideKnowledge(Path file) {
        Path base = knowledgeDir().toAbsolutePath().normalize();
        Path resolved = file.toAbsolutePath().normalize();
        if (!resolved.startsWith(base)) {
            throw new BusinessException(400, "非法路径");
        }
    }
}
