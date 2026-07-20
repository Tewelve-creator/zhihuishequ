package com.community.repair;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.community.repair", "com.community.common"})
@MapperScan("com.community.repair.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.community.common.feign")
public class RepairApplication {
    public static void main(String[] args) {
        SpringApplication.run(RepairApplication.class, args);
    }
}
