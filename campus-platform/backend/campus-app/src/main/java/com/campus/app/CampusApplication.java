package com.campus.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
@EnableScheduling
@ComponentScan("com.campus")
@MapperScan({
        "com.campus.common.mapper",
        "com.campus.app.mapper",
        "com.campus.user.mapper",
        "com.campus.trade.mapper",
        "com.campus.forum.mapper",
        "com.campus.ai.chat.mapper"
})
/** 校园综合服务启动类，聚合 user/trade/forum/ai 等模块的 Mapper 与组件 */
public class CampusApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusApplication.class, args);
    }
}
