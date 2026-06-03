package com.campus.common.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    @Value("${storage.local.path:./uploads}")
    private String uploadPath;

    @Value("${storage.local.url-prefix:http://localhost:8080/uploads}")
    private String urlPrefix;

    @Override
    public String upload(MultipartFile file, String directory) {
        try {
            // 创建目录
            String subDir = directory != null ? directory : "default";
            Path dirPath = Paths.get(uploadPath, subDir);
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

    @Override
    public void delete(String fileUrl) {
        if (fileUrl != null && fileUrl.startsWith(urlPrefix)) {
            String relativePath = fileUrl.substring(urlPrefix.length());
            Path filePath = Paths.get(uploadPath, relativePath);
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException ignored) {
            }
        }
    }
}
