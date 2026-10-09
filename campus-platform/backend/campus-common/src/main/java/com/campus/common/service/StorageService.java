package com.campus.common.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口。屏蔽本地磁盘/对象存储差异，由 storage.type 配置选择实现：
 * local-本地磁盘（默认），oss-对象存储。
 */
public interface StorageService {

    /**
     * 上传文件，返回可访问的URL
     *
     * @param directory 存储子目录（如 avatar、product），为空时落到 default
     * @return 可直接通过 HTTP 访问的文件 URL
     */
    String upload(MultipartFile file, String directory);

    /**
     * 删除文件
     *
     * @param fileUrl 上传时返回的完整 URL，非本服务生成的 URL 会被忽略
     */
    void delete(String fileUrl);
}
