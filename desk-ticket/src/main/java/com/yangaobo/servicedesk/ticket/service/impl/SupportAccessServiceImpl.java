package com.yangaobo.servicedesk.ticket.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.ticket.dto.CatalogEntryDTO;
import com.yangaobo.servicedesk.api.ticket.dto.SupportContextDTO;
import com.yangaobo.servicedesk.api.ticket.dto.SupportEnvironment;
import com.yangaobo.servicedesk.api.ticket.form.CatalogQueryForm;
import com.yangaobo.servicedesk.api.ticket.form.SupportAccessForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.ticket.mapper.DeviceMapper;
import com.yangaobo.servicedesk.ticket.mapper.ProductModelMapper;
import com.yangaobo.servicedesk.ticket.mapper.ServiceItemMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.service.IServiceEntitlementService;
import com.yangaobo.servicedesk.ticket.service.ISupportAccessService;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;

import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 身份在事务外核验，受理资格与资产在所属库短事务核验。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class SupportAccessServiceImpl implements ISupportAccessService {
    private final IdentityVerificationClient identities;
    private final IServiceEntitlementService entitlements;
    private final BusinessTransaction transactions;
    private final TicketMapper tickets;
    private final DeviceMapper devices;
    private final ServiceItemMapper items;
    private final ProductModelMapper models;
    private final ObjectMapper json;

    /**
     * {@inheritDoc}
     */
    @Override
    public SupportContextDTO check(SupportAccessForm form) {
        var actor = identities.verifySession(form);
        if (!Set.of("NEW_SELF_SERVICE", "HISTORY").contains(form.getPurpose()))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        if (form.getTicketId() != null) {
            var ticket = tickets.selectById(form.getTicketId());
            if (ticket == null || !SupportAuthorization.canRead(actor, ticket))
                throw new BusinessException(CommonErrorCode.NOT_FOUND);
            SupportContextDTO result = new SupportContextDTO();
            result.setCustomerId(ticket.getCustomerId());
            result.setServiceItemId(ticket.getServiceItemId());
            result.setDeviceId(ticket.getDeviceId());
            result.setProductModelId(
                    ticket.getDeviceSnapshot() == null
                            ? null
                            : ticket.getDeviceSnapshot().path("product_model_id").asText(null));
            result.setTicketId(ticket.getId());
            result.setTicketVersion(ticket.getRowVersion());
            result.setTitle(ticket.getTitle());
            result.setSymptom(ticket.getSymptom());
            result.setStatus(ticket.getStatus());
            result.setAssigneeId(ticket.getAssigneeId());
            result.setSupportScope(
                    ticket.getServiceScopeSnapshot().path("support_scope").asText(""));
            var node = ticket.getSupportContext();
            SupportEnvironment environment = new SupportEnvironment();
            environment.setOsName(node.path("os_name").asText(null));
            environment.setOsVersion(node.path("os_version").asText(null));
            environment.setSoftwareName(node.path("software_name").asText(null));
            environment.setSoftwareVersion(node.path("software_version").asText(null));
            environment.setConnectionType(node.path("connection_type").asText("UNKNOWN"));
            environment.setAffectedScope(node.path("affected_scope").asText("UNKNOWN"));
            environment.setErrorCode(node.path("error_code").asText(null));
            result.setEnvironment(environment);
            return result;
        }
        SupportAuthorization.role(actor, "CUSTOMER_USER");
        identifier(form.getServiceItemId());
        return transactions.execute(
                () -> {
                    var admission =
                            entitlements.admit(actor.getOrganizationId(), form.getServiceItemId());
                    SupportContextDTO result = new SupportContextDTO();
                    result.setCustomerId(actor.getOrganizationId());
                    result.setServiceItemId(form.getServiceItemId());
                    result.setEnvironment(form.getEnvironment());
                    result.setSupportScope(admission.getItem().getSupportScope());
                    if (form.getDeviceId() != null) {
                        identifier(form.getDeviceId());
                        var device = devices.selectById(form.getDeviceId());
                        if (device == null
                                || !device.getCustomerId().equals(actor.getOrganizationId())
                                || !"ACTIVE".equals(device.getStatus()))
                            throw new BusinessException(CommonErrorCode.FORBIDDEN);
                        result.setDeviceId(device.getId());
                        result.setProductModelId(device.getProductModelId());
                    }
                    return result;
                },
                () -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CatalogEntryDTO> catalog(CatalogQueryForm form, boolean modelQuery) {
        var actor = identities.verifySession(form);
        SupportAuthorization.role(actor, "KNOWLEDGE_ADMIN");
        if (form.getIds() == null || form.getIds().isEmpty() || form.getIds().size() > 50)
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        return form.getIds().stream()
                .sorted()
                .map(
                        id -> {
                            identifier(id);
                            CatalogEntryDTO result = new CatalogEntryDTO();
                            result.setId(id);
                            if (modelQuery) {
                                var model = models.selectById(id);
                                result.setAvailable(
                                        model != null && "ACTIVE".equals(model.getStatus()));
                                if (model != null) result.setCategory(model.getCategory());
                            } else {
                                var item = items.selectById(id);
                                result.setAvailable(
                                        item != null && "ACTIVE".equals(item.getStatus()));
                                if (item != null) result.setCategory(item.getCategory());
                            }
                            return result;
                        })
                .toList();
    }

    /**
     * 内部合同同样校验业务标识，禁止任意文本进入状态查询。
     */
    private void identifier(String value) {
        if (value == null || !value.matches("[0-9a-f]{32}"))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
}
