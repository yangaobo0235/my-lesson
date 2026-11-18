package com.yangaobo.servicedesk.ticket.model.dto.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

import lombok.Data;

/**
 * 本次内部RPC取得的短时证明，只在请求内使用，不作为浏览器授权凭据。
 *
 * @author yangaobo
 */
@Data
public class DraftValidation {
    /**
     * 已确认根标识。
     */
    private String draftRootId;
    /**
     * 本次校验标识。
     */
    private String validationId;
    /**
     * 证明截止时刻。
     */
    private Instant validUntil;
    /**
     * 可持久化的最小交接事实。
     */
    private JsonNode handover;
}
