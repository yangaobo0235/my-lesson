package com.yangaobo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * @author 杨奥博
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsFilter(
            @Value("${cors.allowed-origins:http://localhost:24108}")
            List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedHeaders(List.of("Accept", "Content-Type", "token"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
