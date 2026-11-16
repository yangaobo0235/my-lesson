package com.yangaobo.servicedesk.knowledge.component;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.user.client.UserIdentityClient;
import com.yangaobo.servicedesk.api.user.dto.EligibilityDTO;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.api.user.form.EligibilityForm;
import com.yangaobo.servicedesk.api.user.form.VerifyBrowserForm;
import com.yangaobo.servicedesk.api.user.form.VerifySessionForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import feign.FeignException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * user身份HTTP依赖保护的业务边界。
 *
 * @author yangaobo
 */
@Component
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class IdentityVerificationClient {
    private final UserIdentityClient client;
    private final ObjectMapper objectMapper;
    private final String authorization;

    /**
     * 凭据仅配置在所属调用方，不从浏览器读取。
     */
    public IdentityVerificationClient(
            UserIdentityClient client,
            ObjectMapper objectMapper,
            @Value("${desk.identity.user-token}") String token) {
        if (token.length() < 32) throw new IllegalArgumentException("user服务凭据至少32字符");
        this.client = client;
        this.objectMapper = objectMapper;
        this.authorization = "Bearer " + token;
    }

    /**
     * 核验浏览器身份，唯一发送原Cookie的依赖。
     */
    public IdentityDTO verifyBrowser(VerifyBrowserForm form) {
        return call(() -> client.verify(authorization, form));
    }

    /**
     * 内部业务请求沿原不可登录会话引用重核身份。
     */
    public IdentityDTO verifySession(VerifySessionForm form) {
        // 子类领域表单可能带目录或工单字段；身份合同只发送三项原会话引用。
        var identity = new VerifySessionForm();
        identity.setOwnerId(form.getOwnerId());
        identity.setSessionRef(form.getSessionRef());
        identity.setAuthVersion(form.getAuthVersion());
        return call(() -> client.verifySession(authorization, identity));
    }

    /**
     * 在事务外核验目标客户及可选技术人员。
     */
    public EligibilityDTO eligibility(IdentityDTO actor, String customer, String assignee) {
        EligibilityForm form = new EligibilityForm();
        form.setOwnerId(actor.getOwnerId());
        form.setAuthVersion(actor.getAuthVersion());
        form.setSessionRef(actor.getSessionRef());
        form.setCustomerId(customer);
        form.setAssigneeId(assignee);
        return call(() -> client.eligibility(authorization, form));
    }

    /**
     * Sentinel覆盖实际同步调用，业务拒绝不计依赖故障。
     */
    private <T> T call(Supplier<Result<T>> operation) {
        try (Entry entry = SphU.entry("knowledge:user:identity")) {
            try {
                Result<T> result = operation.get();
                if (result == null || result.getCode() != 0 || result.getData() == null)
                    throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
                return result.getData();
            } catch (FeignException failure) {
                boolean verified =
                        failure.responseHeaders().entrySet().stream()
                                .anyMatch(
                                        header ->
                                                "x-desk-identity-verified"
                                                        .equalsIgnoreCase(header.getKey())
                                                        && header.getValue().contains("1"));
                if (verified) {
                    try {
                        JsonNode body = objectMapper.readTree(failure.contentUTF8());
                        int code = body.path("code").asInt(-1);
                        if (failure.status() == 401 && code == 2001)
                            throw new BusinessException(RemoteIdentityError.UNAUTHENTICATED);
                        if (failure.status() == 401 && code == 2002)
                            throw new BusinessException(RemoteIdentityError.SESSION_EXPIRED);
                        if (failure.status() == 403 && code == 2004)
                            throw new BusinessException(
                                    RemoteIdentityError.PASSWORD_CHANGE_REQUIRED);
                        if (failure.status() == 403 && code == 1003)
                            throw new BusinessException(CommonErrorCode.FORBIDDEN);
                    } catch (JsonProcessingException malformed) {
                        Tracer.traceEntry(malformed, entry);
                        throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
                    }
                }
                Tracer.traceEntry(failure, entry);
                if (failure.getCause() instanceof SocketTimeoutException
                        || failure.getCause() instanceof HttpTimeoutException)
                    throw new BusinessException(CommonErrorCode.DEPENDENCY_TIMEOUT);
                throw new BusinessException(
                        failure.status() >= 500 || failure.status() < 0 || !verified
                                ? CommonErrorCode.SERVICE_UNAVAILABLE
                                : CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
            }
        } catch (BlockException limited) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
