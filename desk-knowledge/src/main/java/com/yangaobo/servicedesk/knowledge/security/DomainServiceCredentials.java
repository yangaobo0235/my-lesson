package com.yangaobo.servicedesk.knowledge.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 校验内部调用方凭据，用户授权仍由身份及领域用例完成。
 *
 * @author yangaobo
 */
@Component
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class DomainServiceCredentials {
    private final byte[] ai;

    /**
     * 每个调用服务凭据独立配置，空凭据拒绝启动。
     */
    public DomainServiceCredentials(@Value("${desk.support.ai-token}") String ai) {
        if (ai.length() < 32) throw new IllegalArgumentException("领域服务凭据缺失");
        this.ai = ("Bearer " + ai).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * RPC仅开放AI实际读取用途。
     */
    public boolean isAi(String value) {
        return equals(ai, value);
    }

    /**
     * 固定时间比较，不记录原凭据。
     */
    private boolean equals(byte[] expected, String actual) {
        return actual != null
                && MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8));
    }
}
