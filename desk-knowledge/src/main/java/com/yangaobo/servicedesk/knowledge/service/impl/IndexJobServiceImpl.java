package com.yangaobo.servicedesk.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.knowledge.component.AiIndexClient;
import com.yangaobo.servicedesk.knowledge.component.BusinessTransaction;
import com.yangaobo.servicedesk.knowledge.component.IndexCommandProducer;
import com.yangaobo.servicedesk.knowledge.component.IndexTrace;
import com.yangaobo.servicedesk.knowledge.component.KnowledgeDigest;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentEventMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentMapper;
import com.yangaobo.servicedesk.knowledge.mapper.IndexJobMapper;
import com.yangaobo.servicedesk.knowledge.model.dto.IndexCommand;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEventEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.IndexJobEntity;
import com.yangaobo.servicedesk.knowledge.model.enums.IndexCommandTypeEnum;
import com.yangaobo.servicedesk.knowledge.model.enums.IndexTransactionStateEnum;
import com.yangaobo.servicedesk.knowledge.model.enums.PublicationStatusEnum;
import com.yangaobo.servicedesk.knowledge.model.form.IndexCreateForm;
import com.yangaobo.servicedesk.knowledge.model.form.KnowledgeQueryForm;
import com.yangaobo.servicedesk.knowledge.model.vo.IndexJobVO;
import com.yangaobo.servicedesk.knowledge.service.IIndexJobService;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeRules;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeViews;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 用业务任务事实协调原生半消息；外部网络调用不持数据库锁。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class IndexJobServiceImpl extends ServiceImpl<IndexJobMapper, IndexJobEntity>
        implements IIndexJobService {
    private static final Logger LOG = LoggerFactory.getLogger(IndexJobServiceImpl.class);
    private final DocumentMapper documents;
    private final DocumentEventMapper events;
    private final BusinessTransaction transactions;
    private final IndexCommandProducer producer;
    private final AiIndexClient ai;
    private final KnowledgeDigest digest;
    private final ObjectMapper json;

    /**
     * 传输追踪不参加请求摘要。
     */
    private final IndexTrace indexTrace;

    /**
     * {@inheritDoc}
     */
    @Override
    public IndexJobVO add(IdentityDTO actor, IndexCreateForm form) {
        KnowledgeRules.administrator(actor);
        String clientHash = digest.hash(form);
        IndexJobEntity previous = request(actor, form.getClientRequestId());
        if (previous != null) return replayReceipt(previous, clientHash);
        DocumentEntity snapshot = documents.selectById(form.getId());
        draft(snapshot, form.getRowVersion());
        String prior = snapshot.getCurrentJobId();
        if (prior != null) settled(baseMapper.selectById(prior));
        boolean[] created = {false};
        IndexJobEntity prepared =
                transactions.execute(
                        () -> {
                            var document = documents.lock(form.getId());
                            var again = request(actor, form.getClientRequestId());
                            if (again != null) {
                                replayReceipt(again, clientHash);
                                return again;
                            }
                            draft(document, form.getRowVersion());
                            if (!Objects.equals(document.getCurrentJobId(), prior))
                                throw new BusinessException(CommonErrorCode.CONFLICT);
                            LocalDateTime now = documents.decisionTime();
                            var job = new IndexJobEntity();
                            job.setId(id());
                            job.setDocumentId(document.getId());
                            job.setAttemptNumber(
                                    Math.toIntExact(
                                            baseMapper.selectCount(
                                                    new LambdaQueryWrapper<IndexJobEntity>()
                                                            .eq(
                                                                    IndexJobEntity
                                                                            ::getDocumentId,
                                                                    document.getId())))
                                            + 1);
                            job.setRequestedBy(actor.getOwnerId());
                            job.setClientRequestId(form.getClientRequestId());
                            var command = new IndexCommand();
                            command.setJobId(job.getId());
                            command.setDocumentId(document.getId());
                            command.setAttemptNumber(job.getAttemptNumber());
                            command.setContentFormat(document.getContentFormat());
                            command.setFileSha256(document.getFileSha256());
                            command.setRequestedBy(actor.getOwnerId());
                            command.setProcessingConfig(form.getProcessingConfig());
                            var input = json.valueToTree(command);
                            ((ObjectNode) input)
                                    .remove(List.of("command", "operationId", "requestHash"));
                            job.setRequestHash(digest.hash(input));
                            command.setRequestHash(job.getRequestHash());
                            job.setRequestPayload(
                                    json.createObjectNode()
                                            .put("schema_version", 1)
                                            .put("client_hash", clientHash)
                                            .set("command_input", json.valueToTree(command)));
                            job.setSubmitState(IndexTransactionStateEnum.PREPARING.getValue());
                            job.setSubmitDeadlineAt(now.plusSeconds(30));
                            job.setCancelState(IndexTransactionStateEnum.NONE.getValue());
                            job.setTraceContext(indexTrace.capture());
                            job.setCreatedAt(now);
                            job.setUpdatedAt(now);
                            baseMapper.insert(job);
                            document.setCurrentJobId(job.getId());
                            touch(document, now);
                            created[0] = true;
                            return job;
                        },
                        () -> request(actor, form.getClientRequestId()));
        // 并发同键只返回准备进度，不重复发送半消息。
        if (!created[0]) return KnowledgeViews.of(prepared);
        return send(
                prepared, IndexCommandTypeEnum.SUBMIT.getValue(), form.getRowVersion() + 1, actor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndexJobVO query(IdentityDTO actor, KnowledgeQueryForm form) {
        KnowledgeRules.administrator(actor);
        if ((form.getId() == null) == (form.getClientRequestId() == null))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        var job =
                form.getId() != null
                        ? baseMapper.selectById(form.getId())
                        : request(actor, form.getClientRequestId());
        if (job == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        var result = KnowledgeViews.of(job);
        var remote = ai.status(job.getId());
        result.setAiAccepted(remote != null);
        if (remote != null) {
            if (!job.getRequestHash().equals(remote.getRequestHash()))
                throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
            result.setAiStatus(remote.getStatus());
            result.setAiStage(remote.hasStage() ? remote.getStage() : null);
            result.setParserState(remote.getParserState());
            result.setReplicasVerified(remote.getReplicasVerified());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndexJobVO cancel(IdentityDTO actor, IdVersionForm form) {
        KnowledgeRules.administrator(actor);
        var initial = baseMapper.selectById(form.getId());
        if (initial == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (Set.of(
                        IndexTransactionStateEnum.PREPARING.getValue(),
                        IndexTransactionStateEnum.COMMITTED.getValue())
                .contains(initial.getCancelState())) return KnowledgeViews.of(initial);
        boolean[] created = {false};
        var job =
                transactions.execute(
                        () -> {
                            var document = documents.lock(initial.getDocumentId());
                            var current = baseMapper.lock(initial.getId());
                            if (Set.of(
                                            IndexTransactionStateEnum.PREPARING.getValue(),
                                            IndexTransactionStateEnum.COMMITTED.getValue())
                                    .contains(current.getCancelState())) return current;
                            draft(document, form.getRowVersion());
                            if (!current.getId().equals(document.getCurrentJobId())
                                    || !IndexTransactionStateEnum.COMMITTED
                                    .getValue()
                                    .equals(current.getSubmitState()))
                                throw new BusinessException(CommonErrorCode.CONFLICT);
                            LocalDateTime now = documents.decisionTime();
                            current.setCancelState(IndexTransactionStateEnum.PREPARING.getValue());
                            current.setCancelOperationId(id());
                            current.setCancelRequestedBy(actor.getOwnerId());
                            current.setCancelDeadlineAt(now.plusSeconds(30));
                            current.setUpdatedAt(now);
                            baseMapper.updateById(current);
                            touch(document, now);
                            created[0] = true;
                            return current;
                        },
                        () -> null);
        return created[0]
                ? send(job, IndexCommandTypeEnum.CANCEL.getValue(), form.getRowVersion() + 1, actor)
                : KnowledgeViews.of(job);
    }

    /**
     * 半消息接收与本地提交分离，二次COMMIT丢失不反向撤销本地事实。
     */
    private IndexJobVO send(
            IndexJobEntity prepared, String type, long preparedVersion, IdentityDTO actor) {
        Transaction half;
        try {
            half = producer.prepare(command(prepared, type));
        } catch (Exception failure) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        boolean committed =
                transactions.execute(
                        () -> {
                            var document = documents.lock(prepared.getDocumentId());
                            var job = baseMapper.lock(prepared.getId());
                            String state =
                                    IndexCommandTypeEnum.SUBMIT.getValue().equals(type)
                                            ? job.getSubmitState()
                                            : job.getCancelState();
                            if (IndexTransactionStateEnum.COMMITTED.getValue().equals(state))
                                return true;
                            if (!IndexTransactionStateEnum.PREPARING.getValue().equals(state))
                                return false;
                            LocalDateTime now = documents.decisionTime();
                            LocalDateTime deadline =
                                    IndexCommandTypeEnum.SUBMIT.getValue().equals(type)
                                            ? job.getSubmitDeadlineAt()
                                            : job.getCancelDeadlineAt();
                            boolean permitted =
                                    PublicationStatusEnum.DRAFT
                                            .getValue()
                                            .equals(document.getPublicationStatus())
                                            && document.getRowVersion() == preparedVersion
                                            && job.getId().equals(document.getCurrentJobId())
                                            && now.isBefore(deadline)
                                            && (IndexCommandTypeEnum.SUBMIT.getValue().equals(type)
                                            || Objects.equals(
                                            job.getCancelOperationId(),
                                            prepared.getCancelOperationId()));
                            if (IndexCommandTypeEnum.SUBMIT.getValue().equals(type)) {
                                job.setSubmitState(
                                        permitted
                                                ? IndexTransactionStateEnum.COMMITTED.getValue()
                                                : IndexTransactionStateEnum.ABORTED.getValue());
                                if (permitted) job.setSubmitCommittedAt(now);
                            } else {
                                job.setCancelState(
                                        permitted
                                                ? IndexTransactionStateEnum.COMMITTED.getValue()
                                                : IndexTransactionStateEnum.ABORTED.getValue());
                                if (permitted) job.setCancelCommittedAt(now);
                            }
                            job.setUpdatedAt(now);
                            baseMapper.updateById(job);
                            touch(document, now);
                            if (permitted)
                                event(
                                        actor,
                                        document.getId(),
                                        IndexCommandTypeEnum.SUBMIT.getValue().equals(type)
                                                ? "INDEX_REQUESTED"
                                                : "INDEX_CANCEL_REQUESTED",
                                        job,
                                        type,
                                        now);
                            return permitted;
                        },
                        () -> null);
        try {
            if (committed) half.commit();
            else half.rollback();
        } catch (Exception failure) {
            LOG.warn(
                    "索引事务二次确认未明 jobId={} command={} committed={}",
                    prepared.getId(),
                    type,
                    committed);
        }
        if (!committed) throw new BusinessException(CommonErrorCode.CONFLICT);
        return KnowledgeViews.of(baseMapper.selectById(prepared.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndexJobVO replay(IdentityDTO actor, String jobId) {
        KnowledgeRules.administrator(actor);
        var job = baseMapper.selectById(jobId);
        if (job == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        String type =
                IndexTransactionStateEnum.COMMITTED.getValue().equals(job.getCancelState())
                        ? IndexCommandTypeEnum.CANCEL.getValue()
                        : IndexCommandTypeEnum.SUBMIT.getValue();
        if (!IndexTransactionStateEnum.COMMITTED.getValue().equals(job.getSubmitState())
                || IndexTransactionStateEnum.PREPARING.getValue().equals(job.getCancelState()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        transactions.execute(
                () -> {
                    event(
                            actor,
                            job.getDocumentId(),
                            "INDEX_REQUEUED",
                            job,
                            type,
                            documents.decisionTime());
                    return true;
                },
                () -> null);
        try {
            producer.prepare(command(job, type)).commit();
        } catch (Exception failure) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        return KnowledgeViews.of(job);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransactionResolution check(IndexCommand command) {
        try {
            var initial = baseMapper.selectById(command.getJobId());
            if (initial == null || !initial.getRequestHash().equals(command.getRequestHash()))
                return TransactionResolution.UNKNOWN;
            var actual = json.valueToTree(command);
            ((ObjectNode) actual).remove(List.of("command", "operationId", "requestHash"));
            if (!initial.getRequestHash().equals(digest.hash(actual)))
                return TransactionResolution.UNKNOWN;
            return transactions.execute(
                    () -> {
                        var document = documents.lock(initial.getDocumentId());
                        var job = baseMapper.lock(initial.getId());
                        if (!IndexCommandTypeEnum.SUBMIT.getValue().equals(command.getCommand())
                                && !IndexCommandTypeEnum.CANCEL
                                .getValue()
                                .equals(command.getCommand()))
                            return TransactionResolution.UNKNOWN;
                        if (IndexCommandTypeEnum.SUBMIT.getValue().equals(command.getCommand())
                                && !command.getOperationId().equals(job.getId()))
                            return TransactionResolution.UNKNOWN;
                        if (IndexCommandTypeEnum.CANCEL.getValue().equals(command.getCommand())
                                && !command.getOperationId().equals(job.getCancelOperationId()))
                            return TransactionResolution.ROLLBACK;
                        String state =
                                IndexCommandTypeEnum.SUBMIT.getValue().equals(command.getCommand())
                                        ? job.getSubmitState()
                                        : job.getCancelState();
                        if (IndexTransactionStateEnum.COMMITTED.getValue().equals(state))
                            return TransactionResolution.COMMIT;
                        if (IndexTransactionStateEnum.ABORTED.getValue().equals(state))
                            return TransactionResolution.ROLLBACK;
                        LocalDateTime deadline =
                                IndexCommandTypeEnum.SUBMIT.getValue().equals(command.getCommand())
                                        ? job.getSubmitDeadlineAt()
                                        : job.getCancelDeadlineAt();
                        LocalDateTime now = documents.decisionTime();
                        if (IndexTransactionStateEnum.PREPARING.getValue().equals(state)
                                && deadline != null
                                && !now.isBefore(deadline)) {
                            if (IndexCommandTypeEnum.SUBMIT.getValue().equals(command.getCommand()))
                                job.setSubmitState(IndexTransactionStateEnum.ABORTED.getValue());
                            else job.setCancelState(IndexTransactionStateEnum.ABORTED.getValue());
                            job.setUpdatedAt(now);
                            baseMapper.updateById(job);
                            touch(document, now);
                            return TransactionResolution.ROLLBACK;
                        }
                        return TransactionResolution.UNKNOWN;
                    },
                    () -> null);
        } catch (Exception unavailable) {
            return TransactionResolution.UNKNOWN;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int expire(int limit) {
        if (limit < 1 || limit > 100) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        LocalDateTime now = documents.decisionTime();
        var candidates = baseMapper.scanPreparingDue(now, limit);
        int count = 0;
        for (var job : candidates)
            if (check(
                    command(
                            job,
                            IndexTransactionStateEnum.PREPARING
                                    .getValue()
                                    .equals(job.getSubmitState())
                                    ? IndexCommandTypeEnum.SUBMIT.getValue()
                                    : IndexCommandTypeEnum.CANCEL.getValue()))
                    == TransactionResolution.ROLLBACK) count++;
        return count;
    }

    /**
     * 前次未明不换任务身份，PDF解析必须已知收敛。
     */
    private void settled(IndexJobEntity job) {
        if (IndexTransactionStateEnum.ABORTED.getValue().equals(job.getSubmitState())) return;
        if (!IndexTransactionStateEnum.COMMITTED.getValue().equals(job.getSubmitState())
                || IndexTransactionStateEnum.PREPARING.getValue().equals(job.getCancelState()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        var remote = ai.status(job.getId());
        if (remote == null
                || !remote.getRequestHash().equals(job.getRequestHash())
                || !Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(remote.getStatus())
                || !Set.of("NOT_REQUIRED", "NOT_SUBMITTED", "SETTLED")
                .contains(remote.getParserState()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 查原发起者请求键，不改变状态。
     */
    private IndexJobEntity request(IdentityDTO actor, String key) {
        return baseMapper.selectOne(
                new LambdaQueryWrapper<IndexJobEntity>()
                        .eq(IndexJobEntity::getRequestedBy, actor.getOwnerId())
                        .eq(IndexJobEntity::getClientRequestId, key));
    }

    /**
     * 原请求异义冲突；ABORTED也不复活。
     */
    private IndexJobVO replayReceipt(IndexJobEntity job, String clientHash) {
        if (!clientHash.equals(job.getRequestPayload().path("client_hash").asText()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return KnowledgeViews.of(job);
    }

    /**
     * 所有文档状态写入均推进版本。
     */
    private void touch(DocumentEntity document, LocalDateTime now) {
        document.setRowVersion(document.getRowVersion() + 1);
        document.setUpdatedAt(now);
        documents.updateById(document);
    }

    /**
     * 只允许当前草稿的精确版本。
     */
    private void draft(DocumentEntity document, long version) {
        if (document == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (!PublicationStatusEnum.DRAFT.getValue().equals(document.getPublicationStatus())
                || document.getRowVersion() != version)
            throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 只有传输指令及操作ID随SUBMIT和CANCEL改变。
     */
    private IndexCommand command(IndexJobEntity job, String type) {
        var command =
                json.convertValue(job.getRequestPayload().get("command_input"), IndexCommand.class);
        command.setCommand(type);
        command.setOperationId(
                IndexCommandTypeEnum.SUBMIT.getValue().equals(type)
                        ? job.getId()
                        : job.getCancelOperationId());
        return command;
    }

    /**
     * 提交与资料事件处于调用者同一本地事务。
     */
    private void event(
            IdentityDTO actor,
            String documentId,
            String type,
            IndexJobEntity job,
            String command,
            LocalDateTime now) {
        var event = new DocumentEventEntity();
        event.setId(id());
        event.setDocumentId(documentId);
        event.setActorId(actor.getOwnerId());
        event.setEventType(type);
        event.setCreatedAt(now);
        event.setDetails(
                json.createObjectNode()
                        .put("schema_version", 1)
                        .put("job_id", job.getId())
                        .put("command", command)
                        .put(
                                "operation_id",
                                IndexCommandTypeEnum.SUBMIT.getValue().equals(command)
                                        ? job.getId()
                                        : job.getCancelOperationId()));
        events.insert(event);
    }

    /**
     * 所属后端分配业务标识。
     */
    private String id() {
        return IdentifierGenerator.next();
    }
}
