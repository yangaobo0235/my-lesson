package com.yangaobo.servicedesk.knowledge;

import com.yangaobo.servicedesk.api.common.v1.ActorRef;
import com.yangaobo.servicedesk.api.knowledge.v1.SourceQuery;
import com.yangaobo.servicedesk.api.ticket.dto.SupportContextDTO;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.knowledge.error.EvidenceErrorCode;
import com.yangaobo.servicedesk.knowledge.component.AiIndexClient;
import com.yangaobo.servicedesk.knowledge.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.knowledge.component.SupportVerificationClient;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentRuleMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentSeriesMapper;
import com.yangaobo.servicedesk.knowledge.mapper.IndexJobMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentRuleEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentSeriesEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.IndexJobEntity;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeAccessService;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 历史资料必须绑定本人实际来源，HISTORY字符串或任意修订ID不产生授权。
 *
 * @author yangaobo
 */
class KnowledgeHistoryTest {
    /**
     * 当前资料事实。
     */
    private DocumentEntity document;
    /**
     * 来源所属服务的实际引用核对边界。
     */
    private AiIndexClient ai;
    /**
     * 被测授权用例。
     */
    private KnowledgeAccessService service;
    /**
     * 仅包含可信会话引用的新检索请求。
     */
    private SourceQuery query;

    /**
     * 真实授权规则执行，隔离其他服务及数据库。
     */
    @BeforeEach
    void setup() {
        var identities = mock(IdentityVerificationClient.class);
        var support = mock(SupportVerificationClient.class);
        var documents = mock(DocumentMapper.class);
        var rules = mock(DocumentRuleMapper.class);
        var series = mock(DocumentSeriesMapper.class);
        var jobs = mock(IndexJobMapper.class);
        ai = mock(AiIndexClient.class);
        service = new KnowledgeAccessService(identities, support, documents, rules, series, jobs, ai);
        var actor = new IdentityDTO();
        actor.setMustChangePassword(false);
        actor.setRoles(Set.of("CUSTOMER_USER"));
        when(identities.verifySession(any())).thenReturn(actor);
        var context = new SupportContextDTO();
        context.setServiceItemId("a".repeat(32));
        when(support.check(any())).thenReturn(context);
        var rule = new DocumentRuleEntity();
        rule.setServiceItemId(context.getServiceItemId());
        rule.setDocumentId("b".repeat(32));
        when(rules.scanForService(context.getServiceItemId(), 25001)).thenReturn(List.of(rule));
        document = new DocumentEntity();
        document.setId(rule.getDocumentId());
        document.setSeriesId("c".repeat(32));
        document.setCurrentJobId("d".repeat(32));
        document.setTitle("打印规程");
        document.setContentFormat("MARKDOWN");
        document.setRowVersion(7L);
        document.setPublicationStatus("RETIRED");
        document.setAccessStatus("ALLOWED");
        when(documents.selectById(document.getId())).thenReturn(document);
        var parent = new DocumentSeriesEntity();
        parent.setAudience("CUSTOMER");
        parent.setSourceType("TROUBLESHOOTING_GUIDE");
        when(series.selectById(document.getSeriesId())).thenReturn(parent);
        var job = new IndexJobEntity();
        job.setId(document.getCurrentJobId());
        job.setSubmitState("COMMITTED");
        job.setCancelState("NONE");
        when(jobs.selectById(job.getId())).thenReturn(job);
        query = SourceQuery.newBuilder().setActor(ActorRef.newBuilder().setOwnerId("e".repeat(32)))
                .setPurpose("HISTORY").setServiceItemId(context.getServiceItemId()).build();
        when(ai.resolve(any())).thenReturn(query.toBuilder().setReferencedDocumentId(document.getId())
                .setReferencedIndexJobId(job.getId()).build());
    }

    /**
     * HISTORY用途只说明支持权限，不能允许新召回退役文档。
     */
    @Test
    void historyPurposeWithoutActualReferenceDoesNotRecallRetiredVersion() {
        assertEquals(0, service.authorized(query).getSourcesCount());
        verifyNoInteractions(ai);
    }

    /**
     * 经过来源服务核验的实际引用可审阅普通换版。
     */
    @Test
    void resolvedActualReferenceCanReviewRetiredVersion() {
        var history = query.toBuilder().setRunId("f".repeat(32)).setSourceUsageId("1".repeat(32)).build();
        var result = service.authorized(history);
        assertEquals(1, result.getSourcesCount());
        assertEquals(document.getId(), result.getSources(0).getDocumentId());
        var old = result.getSources(0).toBuilder().setRowVersion(2L).build();
        assertEquals(7L, service.verify(history, List.of(old)).getSources(0).getRowVersion());
        document.setAccessStatus("REVOKED");
        assertThrows(BusinessException.class, () -> service.verify(history, List.of(old)));
    }

    /**
     * 请求方自行填充固定修订不能绕过实际引用解析。
     */
    @Test
    void fabricatedReferenceIsRejectedBeforeExternalCalls() {
        assertThrows(BusinessException.class, () -> service.authorized(query.toBuilder()
                .setReferencedDocumentId(document.getId()).setReferencedIndexJobId(document.getCurrentJobId()).build()));
        verifyNoInteractions(ai);
    }

    /**
     * 风险撤回不同于普通退役，即使原引用真实也不得交付正文。
     */
    @Test
    void withdrawalStopsHistoricalGuidance() {
        document.setPublicationStatus("WITHDRAWN");
        var history = query.toBuilder().setRunId("f".repeat(32))
                .setSourceUsageId("1".repeat(32)).build();
        var error = assertThrows(BusinessException.class, () -> service.authorized(history));
        assertEquals(EvidenceErrorCode.SOURCE_WITHDRAWN, error.getErrorCode());
        document.setAccessStatus("REVOKED");
        assertEquals(0, service.authorized(history).getSourcesCount());
        assertEquals(0, service.authorized(query).getSourcesCount());
    }
}
