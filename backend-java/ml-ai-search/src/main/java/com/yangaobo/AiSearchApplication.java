package com.yangaobo;

import com.yangaobo.search.config.AiSearchProperties;
import com.yangaobo.component.InternalAiAuthInterceptor;
import com.yangaobo.config.InternalAiWebMvcConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.yangaobo.search")
@EnableConfigurationProperties(AiSearchProperties.class)
@Import({InternalAiAuthInterceptor.class, InternalAiWebMvcConfig.class})
public class AiSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSearchApplication.class, args);
    }
}
