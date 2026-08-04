package com.yangaobo.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sms.aliyun")
public record AliyunSmsProperties(
        String endpoint,
        String accessKeyId,
        String accessKeySecret,
        String signName,
        String templateCode
) {
}
