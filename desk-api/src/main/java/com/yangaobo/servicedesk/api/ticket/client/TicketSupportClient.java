package com.yangaobo.servicedesk.api.ticket.client;

import com.yangaobo.servicedesk.api.ticket.dto.CatalogEntryDTO;
import com.yangaobo.servicedesk.api.ticket.dto.SupportContextDTO;
import com.yangaobo.servicedesk.api.ticket.form.CatalogQueryForm;
import com.yangaobo.servicedesk.api.ticket.form.SupportAccessForm;
import com.yangaobo.servicedesk.common.base.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * knowledge经服务凭据访问ticket当前支持范围及目录资格。
 *
 * @author yangaobo
 */
@FeignClient(name = "desk-ticket", contextId = "ticketSupport", url = "${desk.support.ticket-url:}")
public interface TicketSupportClient {
    /**
     * 按原ActorRef重核当前或历史支持资格。
     */
    @PostMapping("/internal/v1/support/check-access")
    Result<SupportContextDTO> check(
            @RequestHeader("Authorization") String authorization,
            @RequestBody SupportAccessForm form);

    /**
     * 当前有权知识管理员核对项目新增关联资格。
     */
    @PostMapping("/internal/v1/service-items/query")
    Result<java.util.List<CatalogEntryDTO>> services(
            @RequestHeader("Authorization") String authorization,
            @RequestBody CatalogQueryForm form);

    /**
     * 当前有权知识管理员核对型号新增关联资格。
     */
    @PostMapping("/internal/v1/product-models/query")
    Result<java.util.List<CatalogEntryDTO>> models(
            @RequestHeader("Authorization") String authorization,
            @RequestBody CatalogQueryForm form);
}
