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
 * 文件存储配置：type=local 写本地盘；type=minio 写 MinIO。
 * 对外 URL 统一为 /file/static/{tenantId}/{filename}，由接口按 type 读回。
 */
@Component
@Getter
public class FileStorageProperties {

    /** local | minio */
    @Value("${file.type:local}")
    private String type;

    @Value("${file.upload-dir:}")
    private String configured;

    @Value("${file.minio.endpoint:http://127.0.0.1:9000}")
    private String minioEndpoint;

    @Value("${file.minio.access-key:minioadmin}")
    private String minioAccessKey;

    @Value("${file.minio.secret-key:minioadmin}")
    private String minioSecretKey;

    @Value("${file.minio.bucket:smart-community}")
    private String minioBucket;

    private Path root;

    @PostConstruct
    public void init() throws IOException {
        if (StringUtils.hasText(configured)) {
            root = Paths.get(configured).toAbsolutePath().normalize();
        } else {
            root = Paths.get(System.getProperty("user.home"), ".smart-community", "uploads")
                    .toAbsolutePath().normalize();
        }
        Files.createDirectories(root);
    }

    public boolean useMinio() {
        return "minio".equalsIgnoreCase(type);
    }

    public Path resolveLocal(String... parts) {
        Path p = root;
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                p = p.resolve(part);
            }
        }
        return p.normalize();
    }

    /** MinIO object key：tenantId/filename */
    public String objectKey(String tenantId, String filename) {
        return tenantId + "/" + filename;
    }
}
