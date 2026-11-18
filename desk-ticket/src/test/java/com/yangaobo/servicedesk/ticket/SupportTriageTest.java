package com.yangaobo.servicedesk.ticket;

import com.yangaobo.servicedesk.api.ticket.form.SupportAccessForm;
import com.yangaobo.servicedesk.api.ticket.dto.SupportEnvironment;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.error.SupportErrorCode;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.mapper.DeviceMapper;
import com.yangaobo.servicedesk.ticket.mapper.ServiceItemMapper;
import com.yangaobo.servicedesk.ticket.mapper.ProductModelMapper;
import com.yangaobo.servicedesk.ticket.model.dto.support.ServiceAdmission;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceItemEntity;
import com.yangaobo.servicedesk.ticket.service.IServiceEntitlementService;
import com.yangaobo.servicedesk.ticket.service.impl.SupportAccessServiceImpl;

import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

/**
 * 待分类自助只核入场资格，不将入场项目变为已确定诊断范围。
 *
 * @author yangaobo
 */
class SupportTriageTest {
    /**
     * 验证有资格时保留未分类，无资格时拒绝而非扩大知识范围。
     */
    @Test
    void unclassifiedEntryRetainsNullScopeAndRequiresEntitlement() {
        var identity = mock(IdentityVerificationClient.class);
        var entitlements = mock(IServiceEntitlementService.class);
        var transactions = mock(BusinessTransaction.class);
        when(transactions.execute(any(), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
        var actor = new IdentityDTO();
        actor.setOwnerId("a".repeat(32));
        actor.setOrganizationId("b".repeat(32));
        actor.setRoles(Set.of("CUSTOMER_USER"));
        actor.setMustChangePassword(false);
        when(identity.verifySession(any())).thenReturn(actor);
        var item = new ServiceItemEntity();
        item.setId("c".repeat(32));
        item.setSupportScope("仅办公网络");
        var admission = new ServiceAdmission();
        admission.setItem(item);
        when(entitlements.admit(actor.getOrganizationId(), null)).thenReturn(admission);
        var service = new SupportAccessServiceImpl(identity, entitlements, transactions,
                mock(TicketMapper.class), mock(DeviceMapper.class), mock(ServiceItemMapper.class),
                mock(ProductModelMapper.class));
        var form = new SupportAccessForm();
        form.setPurpose("NEW_SELF_SERVICE");
        form.setEnvironment(new SupportEnvironment());
        var context = service.check(form);
        assertNull(context.getServiceItemId());
        assertEquals(actor.getOrganizationId(), context.getCustomerId());
        assertFalse(context.getSupportScope().contains("仅办公网络"));
        verify(entitlements).admit(actor.getOrganizationId(), null);
        when(entitlements.admit(actor.getOrganizationId(), null)).thenThrow(
                new BusinessException(SupportErrorCode.SERVICE_NOT_COVERED));
        var rejected = assertThrows(BusinessException.class, () -> service.check(form));
        assertEquals(SupportErrorCode.SERVICE_NOT_COVERED, rejected.getErrorCode());
    }
}
