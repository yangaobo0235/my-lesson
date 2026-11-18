package com.yangaobo.servicedesk.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.knowledge.component.AiIndexClient;
import com.yangaobo.servicedesk.knowledge.component.BusinessTransaction;
import com.yangaobo.servicedesk.knowledge.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.knowledge.component.KnowledgeDigest;
import com.yangaobo.servicedesk.knowledge.component.OssOriginalStorage;
import com.yangaobo.servicedesk.knowledge.component.SupportVerificationClient;
import com.yangaobo.servicedesk.knowledge.config.RedissonProperties;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentEventMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentSeriesMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentRuleMapper;
import com.yangaobo.servicedesk.knowledge.mapper.IndexJobMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEventEntity;
import com.yangaobo.servicedesk.knowledge.model.enums.DocumentSafetyActionEnum;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentPublishForm;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentSafetyForm;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeRules;
import com.yangaobo.servicedesk.knowledge.service.impl.DocumentServiceImpl;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证安全动作与历史读取语义；事务竞争另用隔离MySQL验证。
 *
 * @author yangaobo
 */
class DocumentSafetyTest {
    /**
     * 资料仓储边界。
     */
    private DocumentMapper documents;
    /**
     * 不可变事件仓储边界。
     */
    private DocumentEventMapper events;
    /**
     * 索引外部依赖。
     */
    private AiIndexClient ai;
    /**
     * 被测完整资料用例。
     */
    private DocumentServiceImpl service;
    /**
     * 当前已发布修订。
     */
    private DocumentEntity document;
    /**
     * 当前操作者。
     */
    private IdentityDTO actor;
    /**
     * 模拟已提交事件，用于验证回执优先级，不证明数据库事务。
     */
    private DocumentEventEntity savedEvent;

    /**
     * 只隔离仓储与外部IO，执行真实动作、摘要、状态及回执转换。
     */
    @BeforeEach
    void setup() {
        documents = mock(DocumentMapper.class);
        events = mock(DocumentEventMapper.class);
        ai = mock(AiIndexClient.class);
        var json = new ObjectMapper().findAndRegisterModules();
        var tx = mock(BusinessTransaction.class);
        doAnswer(call -> call.<Supplier<?>>getArgument(0).get()).when(tx).execute(any(), any());
        service = new DocumentServiceImpl(mock(DocumentSeriesMapper.class), mock(DocumentRuleMapper.class),
                events, mock(IndexJobMapper.class), tx, mock(SupportVerificationClient.class),
                mock(IdentityVerificationClient.class), new KnowledgeDigest(json), mock(OssOriginalStorage.class),
                mock(RedissonClient.class), new RedissonProperties(), ai, json);
        ReflectionTestUtils.setField(service, "baseMapper", documents);
        document = new DocumentEntity();
        document.setId("a".repeat(32));
        document.setSeriesId("b".repeat(32));
        document.setPublicationStatus("PUBLISHED");
        document.setAccessStatus("ALLOWED");
        document.setRowVersion(4L);
        when(documents.selectById(document.getId())).thenReturn(document);
        when(documents.lock(document.getId())).thenReturn(document);
        when(documents.decisionTime()).thenReturn(LocalDateTime.of(2026, 11, 16, 0, 0));
        when(events.selectOne(any())).thenAnswer(call -> savedEvent);
        doAnswer(call -> {
            savedEvent = call.getArgument(0);
            return 1;
        }).when(events).insert(any(DocumentEventEntity.class));
        actor = new IdentityDTO();
        actor.setOwnerId("c".repeat(32));
        actor.setMustChangePassword(false);
        actor.setRoles(Set.of("KNOWLEDGE_ADMIN"));
    }

    /**
     * 生成一次有明确版本的安全动作。
     */
    private DocumentSafetyForm form(String key, long version) {
        var form = new DocumentSafetyForm();
        form.setId(document.getId());
        form.setRowVersion(version);
        form.setClientActionId(key.repeat(32));
        form.setReason("人工核对：该指导存在风险");
        return form;
    }

    /**
     * 原回执优先于已变化的状态和版本，不重复追加事件。
     */
    @Test
    void retryReturnsOriginalSnapshotAfterLaterChange() {
        var form = form("d", 4);
        service.safety(actor, form, DocumentSafetyActionEnum.WITHDRAWN);
        document.setAccessStatus("REVOKED");
        document.setRowVersion(9L);
        var replay = service.safety(actor, form, DocumentSafetyActionEnum.WITHDRAWN);
        assertEquals(5L, replay.getRowVersion());
        assertEquals("WITHDRAWN", replay.getPublicationStatus());
        assertEquals("ALLOWED", replay.getAccessStatus());
        verify(events, times(1)).insert(any(DocumentEventEntity.class));
    }

    /**
     * 同键不能更换原因或用途。
     */
    @Test
    void reusedKeyWithDifferentMeaningIsRejected() {
        var form = form("d", 4);
        service.safety(actor, form, DocumentSafetyActionEnum.WITHDRAWN);
        form.setReason("另一份依据");
        var failure = assertThrows(BusinessException.class,
                () -> service.safety(actor, form, DocumentSafetyActionEnum.WITHDRAWN));
        assertEquals(CommonErrorCode.CONFLICT, failure.getErrorCode());
        verify(events, times(1)).insert(any(DocumentEventEntity.class));
    }

    /**
     * 恢复读取资格不能恢复撤回内容的指导资格。
     */
    @Test
    void restoreAccessKeepsRiskWithdrawal() {
        document.setPublicationStatus("WITHDRAWN");
        document.setWithdrawalReason("存在风险");
        document.setAccessStatus("REVOKED");
        var result = service.safety(actor, form("d", 4), DocumentSafetyActionEnum.ACCESS_RESTORED);
        assertEquals("ALLOWED", result.getAccessStatus());
        assertEquals("WITHDRAWN", result.getPublicationStatus());
        assertFalse(KnowledgeRules.readable(document, true));
    }

    /**
     * 角色变化后不因旧幂等键泄露资料快照。
     */
    @Test
    void receiptStillRequiresCurrentAdministrator() {
        var form = form("d", 4);
        service.safety(actor, form, DocumentSafetyActionEnum.WITHDRAWN);
        actor.setRoles(Set.of("SYSTEM_ADMIN"));
        assertEquals(CommonErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.safety(actor, form, DocumentSafetyActionEnum.WITHDRAWN)).getErrorCode());
    }

    /**
     * 已撤权的草稿在任何外部索引调用前拒绝发布。
     */
    @Test
    void revokedDocumentCannotBePublished() {
        document.setPublicationStatus("DRAFT");
        document.setAccessStatus("REVOKED");
        var form = new DocumentPublishForm();
        form.setId(document.getId());
        form.setRowVersion(4L);
        assertThrows(BusinessException.class, () -> service.publish(actor, form));
        verifyNoInteractions(ai);
    }

    /**
     * 新检索与绑定实际引用的历史审阅具有不同资格。
     */
    @ParameterizedTest
    @CsvSource({"PUBLISHED,true,true", "DISABLED,false,true", "RETIRED,false,true",
            "WITHDRAWN,false,false", "DRAFT,false,false", "DELETED,false,false"})
    void publicationAndAccessRemainIndependent(String status, boolean recall, boolean history) {
        document.setPublicationStatus(status);
        assertEquals(recall, KnowledgeRules.readable(document, false));
        assertEquals(history, KnowledgeRules.readable(document, true));
        document.setAccessStatus("REVOKED");
        assertFalse(KnowledgeRules.readable(document, false));
        assertFalse(KnowledgeRules.readable(document, true));
    }
}
