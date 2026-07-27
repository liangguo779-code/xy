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
 * Best-effort downloader for Hugging Face model files (used by the local ONNX
 * embedder / reranker). If {@code HF_HUB_OFFLINE=1} is set, the downloader skips
 * network access and only validates that the local directory already has the
 * required files.
 *
 * <p>The repo layout expected by langchain4j's BGE / cross-encoder loaders is the
 * standard ONNX export from Hugging Face {@code Xenova/} namespace, e.g.
 * <pre>
 *   model.onnx
 *   tokenizer.json
 *   tokenizer_config.json   (cross-encoder only)
 *   special_tokens_map.json (cross-encoder only)
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
     * Base URL for the Hugging Face model hub. Defaults to the official Chinese
     * mirror so the pipeline works in mainland China without extra config; the
     * Hugging Face canonical host is reachable by setting
     * {@code HF_ENDPOINT=https://huggingface.co} in the environment.
     */
    private static final String HF_ENDPOINT = System.getenv().getOrDefault(
            "HF_ENDPOINT", "https://hf-mirror.com");

    /**
     * Ensure the given local directory contains the listed files. Downloads from
     * {@code {HF_ENDPOINT}/{repo}/resolve/main/{filename}} if any file is missing.
     * Returns the directory path on success.
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

    /** Resolve a path under the AI home directory. */
    public static Path underHome(String home, String... segments) {
        Path p = Paths.get(home);
        for (String s : segments) p = p.resolve(s);
        return p;
    }
}
