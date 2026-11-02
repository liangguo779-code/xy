package com.campus.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 本地上传文件的静态资源映射配置。把 /uploads/** 请求映射到本地存储目录，
 * 使 LocalStorageService 保存的文件可通过 HTTP 直接访问。
 */
@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    /** 本地存储根目录，与 storage.local.path 配置一致 */
    @Value("${storage.local.path:./uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 访问 /uploads/** 时映射到本地文件目录
        // 必须绝对化：file: 位置只接受绝对路径，相对路径会被静默忽略导致 404
        String location = Paths.get(uploadPath).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
