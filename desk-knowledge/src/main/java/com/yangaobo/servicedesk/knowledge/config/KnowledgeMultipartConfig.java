package com.yangaobo.servicedesk.knowledge.config;

import jakarta.servlet.MultipartConfigElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 原生multipart与业务校验复用项目内有界暂存目录，不落系统临时目录。
 *
 * @author yangaobo
 */
@Configuration
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeMultipartConfig {
    /**
     * 创建必需暂存目录后交给原生Servlet文件接收器，生命周期由请求回收。
     */
    @Bean
    public MultipartConfigElement multipartConfigElement(MultipartProperties properties)
            throws IOException {
        Path directory = Path.of(properties.getLocation()).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        properties.setLocation(directory.toString());
        return properties.createMultipartConfig();
    }
}
