package com.yangaobo.servicedesk.ticket.controller.internal;

import com.yangaobo.servicedesk.api.ticket.dto.CatalogEntryDTO;
import com.yangaobo.servicedesk.api.ticket.dto.SupportContextDTO;
import com.yangaobo.servicedesk.api.ticket.form.CatalogQueryForm;
import com.yangaobo.servicedesk.api.ticket.form.SupportAccessForm;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.ticket.security.DomainServiceCredentials;
import com.yangaobo.servicedesk.ticket.service.ISupportAccessService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * knowledge独占凭据的支持范围与目录只读合同。
 *
 * @author yangaobo
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class SupportAccessController {
    private final DomainServiceCredentials credentials;
    private final ISupportAccessService supportAccessService;

    /**
     * 服务认证后仍重核原用户及资源范围。
     */
    @PostMapping("/internal/v1/support/check-access")
    public Result<SupportContextDTO> check(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody SupportAccessForm form,
            HttpServletResponse response) {
        credentials.requireKnowledge(authorization);
        response.setHeader("X-Desk-Support-Verified", "1");
        SupportContextDTO data = supportAccessService.check(form);
        return Result.success(data);
    }

    /**
     * 有界目录新增关联资格，不返回工单正文。
     */
    @PostMapping("/internal/v1/service-items/query")
    public Result<List<CatalogEntryDTO>> services(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CatalogQueryForm form,
            HttpServletResponse response) {
        credentials.requireKnowledge(authorization);
        response.setHeader("X-Desk-Support-Verified", "1");
        List<CatalogEntryDTO> data = supportAccessService.catalog(form, false);
        return Result.success(data);
    }

    /**
     * 有界型号核验，不跨库读取设备。
     */
    @PostMapping("/internal/v1/product-models/query")
    public Result<List<CatalogEntryDTO>> models(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CatalogQueryForm form,
            HttpServletResponse response) {
        credentials.requireKnowledge(authorization);
        response.setHeader("X-Desk-Support-Verified", "1");
        List<CatalogEntryDTO> data = supportAccessService.catalog(form, true);
        return Result.success(data);
    }
}
