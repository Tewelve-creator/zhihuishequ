package com.community.repair.controller;

import com.community.common.result.R;
import com.community.repair.config.FileStorageProperties;
import com.community.repair.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 开发辅助：把本地 uploads 历史图片一次性推到 MinIO。
 * 仅 file.type=minio 时可用。生产环境可删或加权限。
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file.type", havingValue = "minio")
public class FileMigrateController {

    private final FileStorageProperties props;
    private final FileStorageService fileStorageService;

    @PostMapping("/migrate-local-to-minio")
    public R<Map<String, Object>> migrate() throws IOException {
        Path root = props.getRoot();
        int ok = 0;
        int skip = 0;
        int fail = 0;
        if (!Files.isDirectory(root)) {
            return R.ok(Map.of("ok", 0, "skip", 0, "fail", 0, "msg", "本地目录不存在: " + root));
        }
        try (Stream<Path> tenants = Files.list(root)) {
            for (Path tenantDir : tenants.filter(Files::isDirectory).toList()) {
                String tenantId = tenantDir.getFileName().toString();
                try (Stream<Path> files = Files.list(tenantDir)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        String name = file.getFileName().toString();
                        try {
                            byte[] bytes = Files.readAllBytes(file);
                            String ct = Files.probeContentType(file);
                            fileStorageService.putBytes(tenantId, name, bytes, ct);
                            ok++;
                        } catch (Exception e) {
                            fail++;
                            log.warn("migrate fail {}/{}: {}", tenantId, name, e.getMessage());
                        }
                    }
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ok", ok);
        result.put("skip", skip);
        result.put("fail", fail);
        result.put("root", root.toString());
        return R.ok(result);
    }
}
