package com.campus.common.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * 上传文件，返回可访问的URL
     */
    String upload(MultipartFile file, String directory);

    /**
     * 删除文件
     */
    void delete(String fileUrl);
}
