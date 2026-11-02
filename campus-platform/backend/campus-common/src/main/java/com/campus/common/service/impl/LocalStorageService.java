package com.campus.common.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.service.StorageService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地磁盘存储实现。文件写入 storage.local.path 目录，
 * 通过 UploadResourceConfig 映射的 /uploads/** 路径对外访问。
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    /** 本地存储根目录（配置值，可能是相对路径） */
    @Value("${storage.local.path:./uploads}")
    private String uploadPath;

    /** 返回给前端的 URL 前缀，需与 UploadResourceConfig 的映射路径对应 */
    @Value("${storage.local.url-prefix:/uploads}")
    private String urlPrefix;

    /** 归一化后的绝对存储根目录 */
    private Path rootPath;

    /**
     * 把配置里的相对路径解析为绝对路径。
     *
     * <p>必须绝对化：{@code MultipartFile.transferTo(File)} 对相对 File 的解析基准是
     * Tomcat 临时 work 目录，而 {@code Files.createDirectories} 走 JVM 工作目录。
     * 两者不一致会导致「目录建在 backend/uploads、文件写到 Temp/tomcat/.../ROOT/uploads」
     * —— 上传报 FileNotFoundException，图片全丢。{@code UploadResourceConfig} 的
     * {@code file:} 位置同样只接受绝对路径。
     */
    @PostConstruct
    void initRoot() {
        rootPath = Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    /** 上传到 uploadPath/directory 下，文件名用 UUID 重命名避免冲突与中文名问题 */
    @Override
    public String upload(MultipartFile file, String directory) {
        try {
            // 创建目录
            String subDir = directory != null ? directory : "default";
            Path dirPath = rootPath.resolve(subDir);
            Files.createDirectories(dirPath);

            // 生成唯一文件名
            String originalName = file.getOriginalFilename();
            String ext = FileUtil.extName(originalName);
            String fileName = IdUtil.fastSimpleUUID() + "." + ext;

            // 保存文件
            Path filePath = dirPath.resolve(fileName);
            file.transferTo(filePath.toFile());

            // 返回访问URL
            return urlPrefix + "/" + subDir + "/" + fileName;
        } catch (IOException e) {
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    /** 按上传时返回的 URL 反解出本地路径后删除；URL 不属于本存储前缀时静默忽略 */
    @Override
    public void delete(String fileUrl) {
        if (fileUrl != null && fileUrl.startsWith(urlPrefix)) {
            String relativePath = fileUrl.substring(urlPrefix.length());
            Path filePath = rootPath.resolve(relativePath).normalize();
            // 防目录穿越：反解出的路径必须仍在存储根目录内
            if (!filePath.startsWith(rootPath)) {
                return;
            }
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException ignored) {
                // 删除失败不影响主流程（文件可能已被清理）
            }
        }
    }
}
