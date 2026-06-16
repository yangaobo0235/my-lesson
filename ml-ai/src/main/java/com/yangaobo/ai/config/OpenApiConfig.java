package com.yangaobo.ai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mlAiOpenApi(
            @Value("${springdoc.title:MyLesson AI Service}") String title,
            @Value("${springdoc.description:MyLesson AI service APIs}") String description,
            @Value("${springdoc.version:v1.0.0}") String version,
            @Value("${springdoc.author:MyLesson}") String author,
            @Value("${springdoc.url:http://localhost:24107}") String url) {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version)
                        .contact(new Contact()
                                .name(author)
                                .url(url)));
    }
}
