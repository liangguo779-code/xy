package com.campus.ai.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * HuggingFace 模型文件下载器（用于本地 ONNX Embedding / 重排序模型）。
 * 如果设置了 {@code HF_HUB_OFFLINE=1}，则跳过网络访问，仅验证本地目录是否已包含所需文件。
 *
 * <p>langchain4j 的 BGE / cross-encoder 加载器期望的仓库布局是
 * HuggingFace {@code Xenova/} 命名空间的标准 ONNX 导出格式，例如：
 * <pre>
 *   model.onnx
 *   tokenizer.json
 *   tokenizer_config.json   （仅 cross-encoder）
 *   special_tokens_map.json （仅 cross-encoder）
 * </pre>
 */
@Slf4j
public final class HuggingFaceModelLoader {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HuggingFaceModelLoader() {}

    /**
     * HuggingFace 模型仓库的 base URL。默认使用国内镜像，使服务在中国大陆无需额外配置即可工作；
     * 通过设置环境变量 {@code HF_ENDPOINT=https://huggingface.co} 可切换到官方源。
     */
    private static final String HF_ENDPOINT = System.getenv().getOrDefault(
            "HF_ENDPOINT", "https://hf-mirror.com");

    /**
     * 确保指定的本地目录包含所需的文件。如果有文件缺失，
     * 从 {@code {HF_ENDPOINT}/{repo}/resolve/main/{filename}} 下载。
     * 成功时返回目录路径。
     */
    public static Path ensure(String repo, Path dir, List<String> files) throws IOException {
        Files.createDirectories(dir);
        if (Boolean.parseBoolean(System.getenv().getOrDefault("HF_HUB_OFFLINE", "false"))) {
            log.info("HF_HUB_OFFLINE=1 set — verifying {} (offline)", dir);
            for (String f : files) {
                if (!Files.exists(dir.resolve(f))) {
                    throw new IOException("HF_HUB_OFFLINE=1 and missing required file: "
                            + dir.resolve(f));
                }
            }
            return dir;
        }
        for (String f : files) {
            Path target = dir.resolve(f);
            if (Files.exists(target)) continue;
            String url = HF_ENDPOINT + "/" + repo + "/resolve/main/" + f;
            log.info("Downloading {} -> {}", url, target);
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(java.time.Duration.ofMinutes(5))
                        .GET()
                        .build();
                HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + resp.statusCode() + " for " + url);
                }
                Files.createDirectories(target.getParent());
                try (var in = resp.body()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted downloading " + url, e);
            }
        }
        return dir;
    }

    /** 解析 AI 根目录下的路径。 */
    public static Path underHome(String home, String... segments) {
        Path p = Paths.get(home);
        for (String s : segments) p = p.resolve(s);
        return p;
    }
}
