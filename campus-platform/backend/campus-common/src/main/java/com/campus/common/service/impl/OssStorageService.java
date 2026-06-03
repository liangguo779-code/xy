package com.campus.common.service.impl;

import cn.hutool.core.util.IdUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.service.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * OSS 存储实现（示例：阿里云 OSS）
 * 实际使用时需要引入 oss-sdk 依赖并实现具体逻辑
 *
 * 配置:
 *   storage.type=oss
 *   storage.oss.endpoint=oss-cn-hangzhou.aliyuncs.com
 *   storage.oss.bucket=campus-platform
 *   storage.oss.access-key=xxx
 *   storage.oss.secret-key=xxx
 *   storage.oss.url-prefix=https://campus-platform.oss-cn-hangzhou.aliyuncs.com
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "oss")
public class OssStorageService implements StorageService {

    // @Value("${storage.oss.endpoint}")
    // private String endpoint;
    //
    // @Value("${storage.oss.bucket}")
    // private String bucket;
    //
    // @Value("${storage.oss.access-key}")
    // private String accessKey;
    //
    // @Value("${storage.oss.secret-key}")
    // private String secretKey;
    //
    // @Value("${storage.oss.url-prefix}")
    // private String urlPrefix;

    @Override
    public String upload(MultipartFile file, String directory) {
        // TODO: 接入阿里云 OSS / 腾讯云 COS / MinIO 等
        // 示例逻辑：
        // 1. 初始化 OSSClient
        // 2. 生成 objectName = directory + "/" + UUID + "." + ext
        // 3. ossClient.putObject(bucket, objectName, inputStream)
        // 4. return urlPrefix + "/" + objectName

        String subDir = directory != null ? directory : "default";
        String ext = file.getOriginalFilename();
        if (ext != null && ext.contains(".")) {
            ext = ext.substring(ext.lastIndexOf("."));
        } else {
            ext = ".bin";
        }
        String objectName = subDir + "/" + IdUtil.fastSimpleUUID() + ext;

        // 临时方案：直接返回占位URL，实际需替换为OSS SDK调用
        throw new BusinessException("OSS 存储尚未配置，请设置 storage.type=local 或配置 OSS 参数");
    }

    @Override
    public void delete(String fileUrl) {
        // TODO: 接入 OSS 删除
        // ossClient.deleteObject(bucket, objectName)
    }
}
