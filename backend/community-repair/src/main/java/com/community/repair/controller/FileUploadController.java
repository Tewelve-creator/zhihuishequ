package com.community.repair.controller;

import com.community.common.exception.BizException;
import com.community.common.result.R;
import com.community.common.tenant.TenantContext;
import com.community.repair.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageProperties storage;

    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件为空");
        }
        String original = file.getOriginalFilename() == null ? "img.jpg" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".jpg";
        Long tenantId = TenantContext.getTenantId() == null ? 0L : TenantContext.getTenantId();
        Path dir = storage.resolve(String.valueOf(tenantId));
        Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new BizException("非法文件名");
        }
        file.transferTo(target);
        String url = "/file/static/" + tenantId + "/" + name;
        return R.ok(Map.of("url", url, "name", name));
    }

    /** 显式读盘返回，避免 ResourceHandler + 工作目录不一致导致加载失败 */
    @GetMapping("/static/{tenantId}/{filename}")
    public ResponseEntity<Resource> staticFile(
            @PathVariable String tenantId,
            @PathVariable String filename) throws IOException {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BizException(400, "非法文件名");
        }
        Path file = storage.resolve(tenantId, filename);
        if (!Files.isRegularFile(file)) {
            throw new BizException(404, "文件不存在");
        }
        String contentType = Files.probeContentType(file);
        if (contentType == null || contentType.isBlank()) {
            String lower = filename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png")) contentType = MediaType.IMAGE_PNG_VALUE;
            else if (lower.endsWith(".gif")) contentType = MediaType.IMAGE_GIF_VALUE;
            else if (lower.endsWith(".webp")) contentType = "image/webp";
            else contentType = MediaType.IMAGE_JPEG_VALUE;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(file));
    }
}
