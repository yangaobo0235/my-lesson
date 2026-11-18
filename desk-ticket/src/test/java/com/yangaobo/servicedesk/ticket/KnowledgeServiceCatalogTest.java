package com.yangaobo.servicedesk.ticket;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.RequestDigest;
import com.yangaobo.servicedesk.ticket.mapper.ServiceItemMapper;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.service.IServiceEventService;
import com.yangaobo.servicedesk.ticket.service.impl.ServiceItemServiceImpl;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证知识目录用例在读取数据前校验专用角色。
 *
 * @author yangaobo
 */
class KnowledgeServiceCatalogTest {
    /**
     * 保留知识管理员的读取资格，客服和待改密账号均被拒绝。
     */
    @Test
    void knowledgeCatalogRequiresCurrentKnowledgeAdmin() {
        ServiceItemMapper mapper = mock(ServiceItemMapper.class);
        var service = new ServiceItemServiceImpl(mock(IServiceEventService.class), mock(RequestDigest.class),
                mock(BusinessTransaction.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var actor = new IdentityDTO();
        var form = new SupportQueryForm();
        actor.setRoles(Set.of("SERVICE_AGENT"));
        actor.setMustChangePassword(false);
        assertEquals(CommonErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class, () -> service.listKnowledgeItems(actor, form)).getErrorCode());
        actor.setRoles(Set.of("KNOWLEDGE_ADMIN"));
        actor.setMustChangePassword(true);
        assertEquals(CommonErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class, () -> service.listKnowledgeItems(actor, form)).getErrorCode());
        verifyNoInteractions(mapper);
        actor.setMustChangePassword(false);
        assertEquals(Long.valueOf(0), service.listKnowledgeItems(actor, form).getTotal());
    }
}
