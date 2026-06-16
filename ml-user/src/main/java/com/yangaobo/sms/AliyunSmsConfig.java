package com.yangaobo.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AliyunSmsProperties.class)
@ConditionalOnProperty(name = "sms.provider", havingValue = "aliyun")
public class AliyunSmsConfig {

    @Bean
    public Client aliyunSmsClient(AliyunSmsProperties properties) throws Exception {
        Config config = new Config()
                .setAccessKeyId(properties.accessKeyId())
                .setAccessKeySecret(properties.accessKeySecret());
        config.endpoint = properties.endpoint();
        return new Client(config);
    }
}
