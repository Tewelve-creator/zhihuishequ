package com.community.repair.controller;

import com.community.common.exception.BizException;
import com.community.common.result.R;
import com.community.common.tenant.TenantContext;
import com.community.repair.service.FileStorageService;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件为空");
        }
        String original = file.getOriginalFilename() == null ? "img.jpg" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".jpg";
        Long tenantId = TenantContext.getTenantId() == null ? 0L : TenantContext.getTenantId();
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        fileStorageService.store(String.valueOf(tenantId), name, file);
        String url = "/file/static/" + tenantId + "/" + name;
        return R.ok(Map.of("url", url, "name", name));
    }

    /**
     * 统一读图：MinIO / 本地盘。URL 格式不变，前端与 AI 无需改动。
     */
    @GetMapping("/static/{tenantId}/{filename}")
    public ResponseEntity<byte[]> staticFile(
            @PathVariable String tenantId,
            @PathVariable String filename) throws IOException {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BizException(400, "非法文件名");
        }
        FileStorageService.LoadedFile loaded = fileStorageService.load(tenantId, filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.parseMediaType(loaded.contentType()))
                .body(loaded.bytes());
    }
}
