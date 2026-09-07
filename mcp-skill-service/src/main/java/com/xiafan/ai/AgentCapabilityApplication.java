package com.xiafan.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.xiafan.ai.persistence.mapper")
public class AgentCapabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentCapabilityApplication.class, args);
    }
}