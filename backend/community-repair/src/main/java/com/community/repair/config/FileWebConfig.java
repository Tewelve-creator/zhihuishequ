package com.community.repair.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态文件由 {@link com.community.repair.controller.FileUploadController} 按固定目录读盘返回。
 */
@Configuration
public class FileWebConfig implements WebMvcConfigurer {
}
