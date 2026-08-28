package com.agentforge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * AgentForge 平台后端启动类
 * 
 * @author AgentForge Team
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableAsync
@MapperScan("com.agentforge.mapper")
public class AgentForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentForgeApplication.class, args);
    }
}
