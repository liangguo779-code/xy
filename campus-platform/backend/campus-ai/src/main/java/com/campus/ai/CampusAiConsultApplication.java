package com.campus.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.campus.ai", "com.campus.common.config", "com.campus.common.service", "com.campus.common.exception", "com.campus.common.result"})
@MapperScan({"com.campus.ai.mapper", "com.campus.common.mapper"})
public class CampusAiConsultApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAiConsultApplication.class, args);
    }
}
