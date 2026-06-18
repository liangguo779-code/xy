package com.campus.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.campus.user.feign", "com.campus.trade.feign", "com.campus.forum.feign", "com.campus.ai.feign"})
@ComponentScan(basePackages = {"com.campus.admin", "com.campus.common.config", "com.campus.common.service", "com.campus.common.exception", "com.campus.common.result", "com.campus.common.controller"})
@MapperScan({"com.campus.admin.mapper", "com.campus.common.mapper"})
public class CampusApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusApplication.class, args);
    }
}
