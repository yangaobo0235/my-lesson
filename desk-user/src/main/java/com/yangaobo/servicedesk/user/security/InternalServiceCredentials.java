package com.yangaobo.servicedesk.user.security;

import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部调用服务凭据的业务边界。
 *
 * @author yangaobo
 */
@Component
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class InternalServiceCredentials {
    private final byte[] ticketToken;
    private final byte[] knowledgeToken;
    private final byte[] aiToken;

    /** 每个调用方必须使用独立凭据，缺失或复用拒绝启动。 */
    public InternalServiceCredentials(
            @Value("${desk.identity.ticket-token}") String ticket,
            @Value("${desk.identity.knowledge-token}") String knowledge,
            @Value("${desk.identity.ai-token}") String ai) {
        if (ticket.length() < 32
                || knowledge.length() < 32
                || ai.length() < 32
                || ticket.equals(knowledge)
                || ticket.equals(ai)
                || knowledge.equals(ai)) throw new IllegalArgumentException("内部服务凭据必须独立且至少32字符");
        ticketToken = ("Bearer " + ticket).getBytes(StandardCharsets.UTF_8);
        knowledgeToken = ("Bearer " + knowledge).getBytes(StandardCharsets.UTF_8);
        aiToken = ("Bearer " + ai).getBytes(StandardCharsets.UTF_8);
    }

    /** HTTP仅允许Java业务服务，身份资格核验只对ticket开放。 */
    public void requireHttp(String header, boolean eligibility) {
        byte[] actual = header == null ? new byte[0] : header.getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(ticketToken, actual)) return;
        if (!eligibility && MessageDigest.isEqual(knowledgeToken, actual)) return;
        throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    /** RPC只允许AI调用明确身份用途。 */
    public boolean isAi(String header) {
        return header != null
                && MessageDigest.isEqual(aiToken, header.getBytes(StandardCharsets.UTF_8));
    }
}
