package com.community.saas;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.community.saas", "com.community.common"})
@MapperScan("com.community.saas.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.community.common.feign")
@EnableScheduling
public class SaasApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaasApplication.class, args);
    }
}
