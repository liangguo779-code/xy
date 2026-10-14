package com.campus.ai.knowledge;

import com.campus.ai.config.AiProperties;
import com.campus.ai.config.RebuildStatusRegistry;
import com.campus.ai.knowledge.model.ChunkDto;
import com.campus.ai.knowledge.pipeline.DocumentConverter;
import com.campus.ai.knowledge.pipeline.IngestionQueue;
import com.campus.ai.knowledge.pipeline.MarkdownSectionSplitter;
import com.campus.ai.knowledge.store.DisabledFilesCache;
import com.campus.ai.retrieval.VectorStoreFacade;
import com.campus.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 知识库生命周期管理：扫描、上传、启停、删除、重建。对应之前
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
    private final DisabledFilesCache disabled;
    private final RebuildStatusRegistry rebuildStatus;
    /** 入库串行队列，保证 ingest/rebuild 不并发写索引。 */
    private final IngestionQueue ingestionQueue;

    /**
     * 入库失败且旧索引已删、新块未写全的来源（半状态）。
     * 这些来源暂时检索不到，需重新入库/重建恢复；显式记录避免静默丢单一来源全部索引。
     */
    private final Set<String> pendingRepair = ConcurrentHashMap.newKeySet();

    /** 启动时确保知识目录存在；目录为空则尝试从 classpath:knowledge 释放内置种子文档。 */
    @PostConstruct
    void init() throws IOException {
        Path dir = knowledgeDir();
        Files.createDirectories(dir);
        seedIfEmpty(dir);
    }

    /**
     * 应用就绪后检查向量库是否因映射升级（jieba → IK 分词）被重建过。
     * 若是，自动触发知识库全量重建，从磁盘知识文件回填索引，避免手工重建。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildIfIndexMigrated() {
        if (!vectorStore.isPendingRebuild()) return;
        log.info("ES index was recreated with IK analyzers, triggering knowledge rebuild");
        rebuild();
    }

    /** 列出知识库全部允许类型的文件（含大小、后缀、启用状态），按文件名排序。 */
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

    /** 上传知识文件：校验大小/后缀后落盘，入库操作提交到后台队列异步执行。 */
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
        ingestionQueue.submit(() -> {
            try {
                ingestOne(target, safeName);
            } catch (Exception e) {
                // ingestOne 已按失败语义记录日志/待修复状态；
                // 此处拦截，避免异常落入线程池 Future 后被静默吞掉。
                log.warn("上传入库未完成: {}（详见上方错误日志，必要时触发重建）", safeName);
            }
        });
        return Map.of("message", "已提交后台入库", "status", "processing", "name", safeName);
    }

    /** 读取指定文件内容并统一转为 Markdown 返回（PDF/Word 也会转换）。 */
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

    /** 更新文件内容并同步重新入库（同步执行，保证更新接口返回时索引已刷新）。 */
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

    /** 删除文件并同步清除向量库中该来源的全部分块。 */
    public void delete(String filename) {
        Path file = knowledgeDir().resolve(filename).normalize();
        ensureInsideKnowledge(file);
        if (!Files.exists(file)) {
            throw new BusinessException(404, "文件不存在");
        }
        try {
            vectorStore.removeBySource(filename);
            Files.delete(file);
        } catch (IOException e) {
            throw new BusinessException(500, "删除文件失败: " + e.getMessage());
        }
    }

    /** 切换文件启用/禁用状态（禁用后检索时会被过滤）。 */
    public Map<String, Object> toggle(String filename) {
        boolean enabled = disabled.toggle(filename);
        return Map.of("message", enabled ? "已启用" : "已禁用", "enabled", enabled, "name", filename);
    }

    /** 提交全量重建任务到后台队列，前端轮询 rebuildStatus 查询进度。 */
    public Map<String, Object> rebuild() {
        rebuildStatus.reset();
        ingestionQueue.submit(this::rebuildAll);
        return Map.of("message", "已启动重建", "status", "processing");
    }

    /** 获取重建进度快照（总数/已完成百分比/状态）。 */
    public Map<String, Object> rebuildStatus() {
        return rebuildStatus.snapshot();
    }

    /**
     * 全量重建：
     * <ol>
     *   <li>清理孤儿来源 —— 索引（ES）中存在但磁盘上已不存在的来源（如被资源管理器
     *       直接删除、未走删除接口的文件），从两个存储中一并删除，避免残留分块继续被检索到；</li>
     *   <li>仅重新入库启用中的文件；禁用文件跳过（其旧分块保留，检索时仍按禁用过滤，
     *       重新启用后立即可用）；</li>
     *   <li>逐文件更新进度百分比，单文件失败不中断整体流程。</li>
     * </ol>
     */
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
        // 磁盘上现存的全部来源名（含禁用文件 —— 文件还在就不算孤儿）。
        Set<String> onDisk = new HashSet<>();
        for (Path f : files) onDisk.add(f.getFileName().toString());

        // 1. 清理孤儿：索引里有、磁盘上没有的来源全部删除。
        int removedOrphans = 0;
        Set<String> indexed = new HashSet<>(vectorStore.listSources());
        for (String source : indexed) {
            if (!onDisk.contains(source)) {
                vectorStore.removeBySource(source);
                removedOrphans++;
                log.info("Removed orphaned source from index: {}", source);
            }
        }

        // 2. 重新入库启用中的文件；禁用文件跳过入库（检索时按禁用过滤）。
        Set<String> disabledSet = this.disabled.getDisabled();
        List<Path> enabledFiles = new ArrayList<>();
        for (Path f : files) {
            if (!disabledSet.contains(f.getFileName().toString())) enabledFiles.add(f);
        }
        int total = enabledFiles.size();
        int totalChunks = 0;
        int done = 0;
        for (Path f : enabledFiles) {
            try {
                totalChunks += ingestOne(f, f.getFileName().toString());
            } catch (Exception e) {
                log.warn("Failed to ingest {}: {}", f, e.getMessage());
            }
            done++;
            rebuildStatus.updateProgress(total == 0 ? 100 : (int) (done * 100.0 / total));
        }
        rebuildStatus.complete(total, totalChunks, removedOrphans);
    }

    /** 过滤规则：跳过 _ 或 . 开头的隐藏/内部文件，只保留 md/txt/pdf/docx（.doc 旧格式转换器不支持，已剔除）。 */
    private static boolean isAllowedFile(Path p) {
        String name = p.getFileName().toString();
        if (name.startsWith("_") || name.startsWith(".")) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".txt")
                || lower.endsWith(".pdf") || lower.endsWith(".docx");
    }

    /** 组装前端文件列表条目：name/size/suffix/enabled。 */
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

    /**
     * 单文件入库：转 Markdown → 分块 → 先删旧块再写新块，返回分块数。
     * 先删后写保证 rebuild 时切分规则变化不会残留旧索引。
     *
     * <p>失败语义：转换/分块阶段失败不影响索引；一旦进入删除阶段后写入失败，
     * 该来源会进入半状态（旧块已删、新块不全）—— 记入 {@link #pendingRepair}
     * 并以 error 级日志显式告警（提示重建恢复），同时抛出异常让调用方感知失败，
     * 不做静默吞没。
     */
    private int ingestOne(Path file, String name) {
        boolean removedOld = false;
        try {
            String markdown = converter.toMarkdown(file);
            List<ChunkDto> chunks = splitter.split(name, markdown,
                    props.getKnowledge().getChunkSize(),
                    props.getKnowledge().getChunkOverlap());
            // 1. Delete all existing chunks for this source before adding new ones.
            //    This is the original Python behavior (delete-then-add). If addAll fails
            //    afterward, the data is lost — but this is the only way to guarantee
            //    no stale chunks remain when chunk count or indices change.
            vectorStore.removeBySource(name);
            removedOld = true;
            // 2. Add new chunks.
            vectorStore.addAll(chunks);
            pendingRepair.remove(name);
            log.info("Ingested {} chunks from {}", chunks.size(), name);
            return chunks.size();
        } catch (Exception e) {
            if (removedOld) {
                // 半状态：旧索引已删而新块写入失败，该来源暂时检索不到。
                pendingRepair.add(name);
                log.error("入库进入半状态（旧块已删、新块写入失败）: {} — 该来源暂不可检索，"
                        + "请重新上传/重建以恢复；原始错误: {}", name, e.getMessage(), e);
            } else {
                log.warn("Failed to ingest {}（转换/分块阶段失败，索引未受影响）: {}", name, e.getMessage());
            }
            throw new RuntimeException("入库失败: " + name, e);
        }
    }

    /** 当前处于半状态、等待重建修复的来源列表（供运维排查）。 */
    public Set<String> pendingRepairSources() {
        return Set.copyOf(pendingRepair);
    }

    /** 首次启动且知识目录为空时，从 classpath:knowledge 释放内置种子文档（尽力而为，失败不报错）。 */
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

    /** 知识目录：优先取配置的 knowledge.dir，未配置则回退到 home/knowledge。 */
    private Path knowledgeDir() {
        String dir = props.getKnowledge().getDir();
        if (dir == null || dir.isBlank()) {
            dir = Paths.get(props.getHome(), "knowledge").toString();
        }
        return Paths.get(dir);
    }

    /** 校验上传大小不超配置上限。 */
    private void validateSize(MultipartFile file) {
        if (file.getSize() > props.getKnowledge().getMaxFileSize().toBytes()) {
            throw new BusinessException(400, "文件超过大小限制");
        }
    }

    /** 校验后缀在配置的 allowedSuffixes 白名单内。 */
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

    /** 路径穿越防护：解析后的路径必须仍位于知识目录内，否则拒绝。 */
    private void ensureInsideKnowledge(Path file) {
        Path base = knowledgeDir().toAbsolutePath().normalize();
        Path resolved = file.toAbsolutePath().normalize();
        if (!resolved.startsWith(base)) {
            throw new BusinessException(400, "非法路径");
        }
    }
}
