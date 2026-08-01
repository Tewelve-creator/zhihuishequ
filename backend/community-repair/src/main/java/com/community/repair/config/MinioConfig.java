package com.community.repair.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "file.type", havingValue = "minio")
public class MinioConfig {

    @Bean
    public MinioClient minioClient(FileStorageProperties props) {
        return MinioClient.builder()
                .endpoint(props.getMinioEndpoint())
                .credentials(props.getMinioAccessKey(), props.getMinioSecretKey())
                .build();
    }

    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient client, FileStorageProperties props) {
        return args -> {
            try {
                String bucket = props.getMinioBucket();
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO bucket created: {}", bucket);
                }
                log.info("File storage=minio endpoint={} bucket={}", props.getMinioEndpoint(), bucket);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "MinIO 连接失败，请先启动 MinIO（默认 http://127.0.0.1:9000）并核对 file.minio.* 配置："
                                + e.getMessage(),
                        e);
            }
        };
    }
}
