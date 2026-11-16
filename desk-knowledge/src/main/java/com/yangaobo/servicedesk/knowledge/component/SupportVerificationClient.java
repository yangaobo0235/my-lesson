package com.yangaobo.servicedesk.knowledge.component;

import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.ticket.client.TicketSupportClient;
import com.yangaobo.servicedesk.api.ticket.dto.SupportContextDTO;
import com.yangaobo.servicedesk.api.ticket.form.CatalogQueryForm;
import com.yangaobo.servicedesk.api.ticket.form.SupportAccessForm;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.exception.BusinessException;

import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * knowledge经ticket权威合同核验支持范围及适用规则关联。
 *
 * @author yangaobo
 */
@Component
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class SupportVerificationClient {
    private final TicketSupportClient client;
    private final String authorization;
    private final ObjectMapper json;

    /**
     * 服务凭据只从所属配置取得，不接收浏览器覆盖。
     */
    public SupportVerificationClient(
            TicketSupportClient client,
            ObjectMapper json,
            @Value("${desk.support.ticket-token}") String token) {
        if (token.length() < 32) throw new IllegalArgumentException("ticket内部凭据缺失");
        this.client = client;
        this.json = json;
        authorization = "Bearer " + token;
    }

    /**
     * 范围内新自助或既有工单受理事实，不跨库读取。
     */
    public SupportContextDTO check(SupportAccessForm form) {
        return call(() -> client.check(authorization, form));
    }

    /**
     * 关联前核对项目与可选型号；空集合不得扩大成全目录。
     */
    public void catalog(IdentityDTO actor, Set<String> ids, boolean models) {
        if (ids.isEmpty()) return;
        CatalogQueryForm form = new CatalogQueryForm();
        form.setOwnerId(actor.getOwnerId());
        form.setSessionRef(actor.getSessionRef());
        form.setAuthVersion(actor.getAuthVersion());
        form.setIds(ids);
        var entries =
                call(
                        () ->
                                models
                                        ? client.models(authorization, form)
                                        : client.services(authorization, form));
        if (entries.size() != ids.size()
                || entries.stream().anyMatch(entry -> !entry.isAvailable()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 实际Feign调用限定Sentinel生命周期，失败不伪装无资料。
     */
    private <T> T call(Supplier<Result<T>> request) {
        try (var entry = SphU.entry("knowledge:ticket:support")) {
            try {
                var result = request.get();
                if (result == null || result.getCode() != 0 || result.getData() == null)
                    throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
                return result.getData();
            } catch (feign.FeignException failure) {
                boolean verified =
                        failure.responseHeaders().entrySet().stream()
                                .anyMatch(
                                        header ->
                                                "x-desk-support-verified"
                                                        .equalsIgnoreCase(header.getKey())
                                                        && header.getValue().contains("1"));
                if (verified) {
                    try {
                        int code = json.readTree(failure.contentUTF8()).path("code").asInt(-1);
                        if (failure.status() == 409 && code == 3001)
                            throw new BusinessException(RemoteSupportError.SERVICE_NOT_COVERED);
                        if (failure.status() == 401 && code == 2001)
                            throw new BusinessException(RemoteIdentityError.UNAUTHENTICATED);
                        if (failure.status() == 401 && code == 2002)
                            throw new BusinessException(RemoteIdentityError.SESSION_EXPIRED);
                        if (failure.status() == 403 && code == 2004)
                            throw new BusinessException(
                                    RemoteIdentityError.PASSWORD_CHANGE_REQUIRED);
                        if (failure.status() == 403 && code == 1003)
                            throw new BusinessException(CommonErrorCode.FORBIDDEN);
                        if (failure.status() == 404 && code == 1002)
                            throw new BusinessException(CommonErrorCode.NOT_FOUND);
                        if (failure.status() == 409 && code == 1004)
                            throw new BusinessException(CommonErrorCode.CONFLICT);
                    } catch (JsonProcessingException malformed) {
                        throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
                    }
                }
                Tracer.traceEntry(failure, entry);
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            }
        } catch (BlockException failure) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
