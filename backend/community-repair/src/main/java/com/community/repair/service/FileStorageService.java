package com.community.repair.service;

import com.community.common.exception.BizException;
import com.community.repair.config.FileStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageProperties props;
    private final ObjectProvider<MinioClient> minioClient;

    public void store(String tenantId, String filename, MultipartFile file) throws IOException {
        if (props.useMinio()) {
            storeMinio(tenantId, filename, file);
        } else {
            storeLocal(tenantId, filename, file);
        }
    }

    public LoadedFile load(String tenantId, String filename) throws IOException {
        if (props.useMinio()) {
            try {
                return loadMinio(tenantId, filename);
            } catch (BizException e) {
                // 兼容切换前已落盘的历史图片
                LoadedFile local = tryLoadLocal(tenantId, filename);
                if (local != null) {
                    return local;
                }
                throw e;
            }
        }
        LoadedFile local = tryLoadLocal(tenantId, filename);
        if (local == null) {
            throw new BizException(404, "文件不存在");
        }
        return local;
    }

    private void storeLocal(String tenantId, String filename, MultipartFile file) throws IOException {
        Path dir = props.resolveLocal(tenantId);
        Files.createDirectories(dir);
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir)) {
            throw new BizException("非法文件名");
        }
        file.transferTo(target);
    }

    private void storeMinio(String tenantId, String filename, MultipartFile file) {
        MinioClient client = requireMinio();
        String key = props.objectKey(tenantId, filename);
        String contentType = guessContentType(filename, file.getContentType());
        try (InputStream in = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(props.getMinioBucket())
                    .object(key)
                    .stream(in, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new BizException("上传到 MinIO 失败：" + e.getMessage());
        }
    }

    private LoadedFile loadMinio(String tenantId, String filename) {
        MinioClient client = requireMinio();
        String key = props.objectKey(tenantId, filename);
        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(props.getMinioBucket())
                    .object(key)
                    .build());
            try (InputStream in = client.getObject(GetObjectArgs.builder()
                    .bucket(props.getMinioBucket())
                    .object(key)
                    .build())) {
                byte[] bytes = in.readAllBytes();
                return new LoadedFile(bytes, guessContentType(filename, null));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("NoSuchKey") || msg.contains("Not Found") || msg.contains("does not exist")) {
                throw new BizException(404, "文件不存在");
            }
            throw new BizException("从 MinIO 读取失败：" + msg);
        }
    }

    private LoadedFile tryLoadLocal(String tenantId, String filename) throws IOException {
        Path file = props.resolveLocal(tenantId, filename);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(file);
        String contentType = Files.probeContentType(file);
        return new LoadedFile(bytes, guessContentType(filename, contentType));
    }

    private MinioClient requireMinio() {
        MinioClient client = minioClient.getIfAvailable();
        if (client == null) {
            throw new BizException("MinIO 未初始化，请检查 file.type=minio 与连接配置");
        }
        return client;
    }

    private String guessContentType(String filename, String hint) {
        if (hint != null && !hint.isBlank() && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(hint)) {
            return hint;
        }
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /** 供迁移脚本把本地文件推到 MinIO */
    public void putBytes(String tenantId, String filename, byte[] bytes, String contentType) {
        MinioClient client = requireMinio();
        String key = props.objectKey(tenantId, filename);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(props.getMinioBucket())
                    .object(key)
                    .stream(in, bytes.length, -1)
                    .contentType(guessContentType(filename, contentType))
                    .build());
        } catch (Exception e) {
            throw new BizException("写入 MinIO 失败：" + e.getMessage());
        }
    }

    public record LoadedFile(byte[] bytes, String contentType) {}
}
