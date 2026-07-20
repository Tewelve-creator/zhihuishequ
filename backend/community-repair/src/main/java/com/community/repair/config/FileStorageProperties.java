package com.community.repair.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一上传根目录：优先 FILE_UPLOAD_DIR，否则使用稳定绝对路径，避免 jar 工作目录变化导致图片 404。
 */
@Component
@Getter
public class FileStorageProperties {

    @Value("${file.upload-dir:}")
    private String configured;

    private Path root;

    @PostConstruct
    public void init() throws IOException {
        if (StringUtils.hasText(configured)) {
            root = Paths.get(configured).toAbsolutePath().normalize();
        } else {
            // 默认：用户目录下，不依赖启动 cwd
            root = Paths.get(System.getProperty("user.home"), ".smart-community", "uploads")
                    .toAbsolutePath().normalize();
        }
        Files.createDirectories(root);
    }

    public Path resolve(String... parts) {
        Path p = root;
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                p = p.resolve(part);
            }
        }
        return p.normalize();
    }
}
