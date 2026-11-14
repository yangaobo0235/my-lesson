package com.yangaobo.servicedesk.knowledge.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;

/**
 * 原生multipart与业务校验复用项目内有界暂存目录，不落系统临时目录。
 *
 * @author yangaobo
 */
@org.springframework.context.annotation.Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeMultipartConfig {
    /** 创建必需暂存目录后交给原生Servlet文件接收器，生命周期由请求回收。 */
    @org.springframework.context.annotation.Bean
    public MultipartConfigElement multipartConfigElement(MultipartProperties properties) throws java.io.IOException {
        java.nio.file.Path directory=java.nio.file.Path.of(properties.getLocation()).toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(directory);properties.setLocation(directory.toString());
        return properties.createMultipartConfig();
    }
}
