package com.campus.common.controller;

import com.campus.common.result.R;
import com.campus.common.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传接口。按图片/视频/通用文件分端点，各端点有独立的类型与大小限制，
 * 存储实现由 StorageService 按 storage.type 配置决定。
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final StorageService storageService;

    /** 上传图片（仅 image/*，限 10MB），返回 {"url": "..."} */
    @PostMapping("/image")
    public R<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        validateImage(file);
        String url = storageService.upload(file, "images");
        return R.ok(Map.of("url", url));
    }

    /** 上传视频（仅 video/*，限 50MB），返回 {"url": "..."} */
    @PostMapping("/video")
    public R<Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file) {
        validateVideo(file);
        String url = storageService.upload(file, "videos");
        return R.ok(Map.of("url", url));
    }

    /** 通用文件上传（限 20MB，禁止可执行后缀），返回 {"url": "..."} */
    @PostMapping("/file")
    public R<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "files") String directory) {
        // 通用文件上传校验
        if (file.isEmpty()) {
            throw new com.campus.common.exception.BusinessException("文件不能为空");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new com.campus.common.exception.BusinessException("文件大小不能超过20MB");
        }
        // 禁止上传可执行文件
        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            String ext = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
            // 黑名单脚本/可执行后缀，防止上传 webshell 之类的可执行体
            if (ext.matches("exe|sh|bat|cmd|jsp|php|py|rb")) {
                throw new com.campus.common.exception.BusinessException("禁止上传可执行文件");
            }
        }
        String url = storageService.upload(file, directory);
        return R.ok(Map.of("url", url));
    }

    /** 图片校验：非空、contentType 以 image/ 开头、不超过 10MB */
    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new com.campus.common.exception.BusinessException("文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new com.campus.common.exception.BusinessException("只能上传图片文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new com.campus.common.exception.BusinessException("图片大小不能超过10MB");
        }
    }

    /** 视频校验：非空、contentType 以 video/ 开头、不超过 50MB */
    private void validateVideo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new com.campus.common.exception.BusinessException("文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new com.campus.common.exception.BusinessException("只能上传视频文件");
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new com.campus.common.exception.BusinessException("视频大小不能超过50MB");
        }
    }
}
