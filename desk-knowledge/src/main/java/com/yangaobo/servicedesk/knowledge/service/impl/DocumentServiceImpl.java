package com.yangaobo.servicedesk.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.api.user.form.VerifySessionForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.knowledge.component.AiIndexClient;
import com.yangaobo.servicedesk.knowledge.component.BusinessTransaction;
import com.yangaobo.servicedesk.knowledge.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.knowledge.component.KnowledgeDigest;
import com.yangaobo.servicedesk.knowledge.component.OssOriginalStorage;
import com.yangaobo.servicedesk.knowledge.component.SupportVerificationClient;
import com.yangaobo.servicedesk.knowledge.config.RedissonProperties;
import com.yangaobo.servicedesk.knowledge.error.DocumentErrorCode;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentEventMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentRuleMapper;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentSeriesMapper;
import com.yangaobo.servicedesk.knowledge.mapper.IndexJobMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEventEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentRuleEntity;
import com.yangaobo.servicedesk.knowledge.model.entity.IndexJobEntity;
import com.yangaobo.servicedesk.knowledge.model.enums.ContentFormatEnum;
import com.yangaobo.servicedesk.knowledge.model.enums.IndexCommandTypeEnum;
import com.yangaobo.servicedesk.knowledge.model.enums.IndexTransactionStateEnum;
import com.yangaobo.servicedesk.knowledge.model.enums.PublicationStatusEnum;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentPublishForm;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentRuleForm;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentUpdateForm;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentUploadForm;
import com.yangaobo.servicedesk.knowledge.model.form.KnowledgeQueryForm;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentEventVO;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentVO;
import com.yangaobo.servicedesk.knowledge.service.IDocumentService;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeRules;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeViews;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 文件、互斥、版本与发布由完整用例编排；长外部操作不持数据库事务。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, DocumentEntity>
        implements IDocumentService {
    private final DocumentSeriesMapper documentSeriesMapper;
    private final DocumentRuleMapper documentRuleMapper;
    private final DocumentEventMapper documentEventMapper;
    private final IndexJobMapper indexJobMapper;
    private final BusinessTransaction businessTransaction;
    private final SupportVerificationClient supportVerificationClient;
    private final IdentityVerificationClient identityVerificationClient;
    private final KnowledgeDigest knowledgeDigest;
    private final OssOriginalStorage originalStorage;
    private final RedissonClient redissonClient;
    private final RedissonProperties lockProperties;
    private final AiIndexClient aiIndexClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.servlet.multipart.location}")
    private String uploadDirectory;

    /**
     * {@inheritDoc}
     */
    @Override
    public DocumentVO upload(
            IdentityDTO actor,
            DocumentUploadForm form,
            String filename,
            String contentType,
            InputStream input)
            throws IOException {
        KnowledgeRules.administrator(actor);
        normalizeRules(form.getRules());
        if (filename == null
                || filename.isBlank()
                || filename.length() > 255
                || filename.contains("/")
                || filename.contains("\\")
                || filename.chars().anyMatch(c -> c < 32))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        boolean markdown = ContentFormatEnum.MARKDOWN.getValue().equals(form.getContentFormat());
        if (!(markdown
                ? Set.of("text/markdown", "text/plain", "application/octet-stream")
                : Set.of("application/pdf"))
                .contains(contentType))
            throw new BusinessException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "original-", ".tmp");
        try {
            long size = 0;
            MessageDigest sha;
            try {
                sha = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
            try (var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > (markdown ? 1048576 : 20971520))
                        throw new BusinessException(CommonErrorCode.PAYLOAD_TOO_LARGE);
                    output.write(buffer, 0, count);
                    sha.update(buffer, 0, count);
                }
            }
            if (size == 0)
                throw new BusinessException(
                        markdown ? CommonErrorCode.INVALID_REQUEST : DocumentErrorCode.EMPTY_PDF);
            if (markdown) validateMarkdown(Files.readAllBytes(temporary));
            else
                try (var header = Files.newInputStream(temporary)) {
                    if (!Arrays.equals(
                            header.readNBytes(5), "%PDF-".getBytes(StandardCharsets.US_ASCII)))
                        throw new BusinessException(DocumentErrorCode.INVALID_PDF_TYPE);
                }
            String fileSha = HexFormat.of().formatHex(sha.digest());
            String hash =
                    knowledgeDigest.hash(
                            objectMapper
                                    .createObjectNode()
                                    .set("metadata", objectMapper.valueToTree(form)));
            hash = knowledgeDigest.hash(List.of(hash, fileSha));
            String requestHash = hash;
            var old = uploadReceipt(actor, form.getClientUploadId(), hash);
            if (old != null) return view(old);
            var lock =
                    redissonClient.getLock(
                            lockProperties.getKeyPrefix()
                                    + actor.getOwnerId()
                                    + ":"
                                    + form.getClientUploadId());
            boolean acquired;
            try {
                acquired = lock.tryLock(lockProperties.getWaitTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            } catch (RuntimeException unavailable) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            }
            if (!acquired) throw new BusinessException(DocumentErrorCode.UPLOAD_IN_PROGRESS);
            try {
                old = uploadReceipt(actor, form.getClientUploadId(), hash);
                if (old != null) return view(old);
                VerifySessionForm ref = new VerifySessionForm();
                ref.setOwnerId(actor.getOwnerId());
                ref.setSessionRef(actor.getSessionRef());
                ref.setAuthVersion(actor.getAuthVersion());
                KnowledgeRules.administrator(identityVerificationClient.verifySession(ref));
                validateRules(actor, form.getRules());
                var stored = originalStorage.put(temporary, form.getContentFormat(), size);
                if (!lock.isHeldByCurrentThread())
                    throw new BusinessException(CommonErrorCode.CONFLICT);
                long byteCount = size;
                var document =
                        businessTransaction.execute(
                                () -> {
                                    var parent = documentSeriesMapper.lock(form.getSeriesId());
                                    if (parent == null)
                                        throw new BusinessException(CommonErrorCode.NOT_FOUND);
                                    var existing =
                                            uploadReceipt(
                                                    actor, form.getClientUploadId(), requestHash);
                                    if (existing != null) return existing;
                                    LocalDateTime now = baseMapper.decisionTime();
                                    var value = new DocumentEntity();
                                    value.setId(id());
                                    value.setSeriesId(parent.getId());
                                    value.setClientUploadId(form.getClientUploadId());
                                    value.setUploadRequestHash(requestHash);
                                    value.setRevisionNumber(
                                            Math.toIntExact(
                                                    baseMapper.selectCount(
                                                            new LambdaQueryWrapper<
                                                                    DocumentEntity>()
                                                                    .eq(
                                                                            DocumentEntity
                                                                                    ::getSeriesId,
                                                                            parent
                                                                                    .getId())))
                                                    + 1);
                                    value.setTitle(form.getTitle().strip());
                                    value.setContentFormat(form.getContentFormat());
                                    value.setOriginalFileName(filename);
                                    value.setStorageBucket(stored.getBucket());
                                    value.setStorageKey(stored.getKey());
                                    value.setStorageVersionId(stored.getVersionId());
                                    value.setFileSizeBytes(byteCount);
                                    value.setFileSha256(fileSha);
                                    value.setSourceUrl(form.getSourceUrl());
                                    value.setSourceReviewNote(form.getSourceReviewNote());
                                    value.setRevisionLabel(form.getRevisionLabel());
                                    value.setIssuedOn(form.getIssuedOn());
                                    value.setApplicabilityNote(form.getApplicabilityNote());
                                    value.setPublicationStatus(
                                            PublicationStatusEnum.DRAFT.getValue());
                                    value.setCreatedBy(actor.getOwnerId());
                                    value.setCreatedAt(now);
                                    value.setUpdatedAt(now);
                                    value.setRowVersion(0L);
                                    baseMapper.insert(value);
                                    replaceRules(value.getId(), form.getRules(), now);
                                    event(actor, value, "UPLOADED", null, null, now);
                                    return value;
                                },
                                () -> uploadReceipt(actor, form.getClientUploadId(), requestHash));
                return view(document);
            } finally {
                if (lock.isHeldByCurrentThread()) lock.unlock();
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DocumentVO query(IdentityDTO actor, KnowledgeQueryForm form) {
        KnowledgeRules.administrator(actor);
        if ((form.getId() == null) == (form.getClientUploadId() == null))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        var document =
                form.getId() != null
                        ? baseMapper.selectById(form.getId())
                        : uploadReceipt(actor, form.getClientUploadId(), null);
        if (document == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return view(document);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<DocumentVO> list(IdentityDTO actor, KnowledgeQueryForm form) {
        KnowledgeRules.administrator(actor);
        long total = baseMapper.countDocuments(form.getSeriesId());
        var records = baseMapper.listDocuments(form.getSeriesId(), form.getPageSize(), form.offset())
                .stream().map(this::view).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DocumentVO update(IdentityDTO actor, DocumentUpdateForm form) {
        KnowledgeRules.administrator(actor);
        validateRules(actor, form.getRules());
        return businessTransaction.execute(
                () -> {
                    var document = baseMapper.lock(form.getId());
                    version(document, form.getRowVersion());
                    if (!PublicationStatusEnum.DRAFT
                            .getValue()
                            .equals(document.getPublicationStatus()))
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    var before = view(document);
                    LocalDateTime now = baseMapper.decisionTime();
                    document.setTitle(form.getTitle().strip());
                    document.setSourceUrl(form.getSourceUrl());
                    document.setSourceReviewNote(form.getSourceReviewNote());
                    document.setRevisionLabel(form.getRevisionLabel());
                    document.setIssuedOn(form.getIssuedOn());
                    document.setApplicabilityNote(form.getApplicabilityNote());
                    replaceRules(document.getId(), form.getRules(), now);
                    touch(document, now);
                    event(actor, document, "METADATA_CHANGED", null, before, now);
                    return view(document);
                },
                () -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DocumentVO publish(IdentityDTO actor, DocumentPublishForm form) {
        KnowledgeRules.administrator(actor);
        var snapshot = baseMapper.selectById(form.getId());
        version(snapshot, form.getRowVersion());
        ready(snapshot);
        return businessTransaction.execute(
                () -> {
                    documentSeriesMapper.lock(snapshot.getSeriesId());
                    var document = baseMapper.lock(form.getId());
                    version(document, form.getRowVersion());
                    if (!PublicationStatusEnum.DRAFT
                            .getValue()
                            .equals(document.getPublicationStatus())
                            || !Objects.equals(
                            snapshot.getCurrentJobId(), document.getCurrentJobId()))
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    localReady(document);
                    var before = view(document);
                    LocalDateTime now = baseMapper.decisionTime();
                    var current =
                            baseMapper.selectOne(
                                    new LambdaQueryWrapper<DocumentEntity>()
                                            .eq(DocumentEntity::getSeriesId, document.getSeriesId())
                                            .in(
                                                    DocumentEntity::getPublicationStatus,
                                                    PublicationStatusEnum.PUBLISHED.getValue(),
                                                    PublicationStatusEnum.DISABLED.getValue()));
                    if (current != null) {
                        current = baseMapper.lock(current.getId());
                        if (!current.getId().equals(form.getReplacesDocumentId())
                                || current.getRevisionNumber() >= document.getRevisionNumber())
                            throw new BusinessException(CommonErrorCode.CONFLICT);
                        var prior = view(current);
                        current.setPublicationStatus(PublicationStatusEnum.RETIRED.getValue());
                        touch(current, now);
                        document.setReplacesDocumentId(current.getId());
                        event(
                                actor,
                                current,
                                PublicationStatusEnum.RETIRED.getValue(),
                                document.getId(),
                                prior,
                                now);
                    } else if (form.getReplacesDocumentId() != null)
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    document.setPublicationStatus(PublicationStatusEnum.PUBLISHED.getValue());
                    document.setPublishedAt(now);
                    document.setPublishedBy(actor.getOwnerId());
                    touch(document, now);
                    event(
                            actor,
                            document,
                            PublicationStatusEnum.PUBLISHED.getValue(),
                            current == null ? null : current.getId(),
                            before,
                            now);
                    return view(document);
                },
                () -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DocumentVO availability(IdentityDTO actor, IdVersionForm form, boolean restore) {
        KnowledgeRules.administrator(actor);
        var snapshot = baseMapper.selectById(form.getId());
        version(snapshot, form.getRowVersion());
        if (restore) ready(snapshot);
        return businessTransaction.execute(
                () -> {
                    documentSeriesMapper.lock(snapshot.getSeriesId());
                    var document = baseMapper.lock(form.getId());
                    version(document, form.getRowVersion());
                    if (!(restore
                            ? PublicationStatusEnum.DISABLED.getValue()
                            : PublicationStatusEnum.PUBLISHED.getValue())
                            .equals(document.getPublicationStatus()))
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    if (restore) localReady(document);
                    var before = view(document);
                    LocalDateTime now = baseMapper.decisionTime();
                    document.setPublicationStatus(
                            restore
                                    ? PublicationStatusEnum.PUBLISHED.getValue()
                                    : PublicationStatusEnum.DISABLED.getValue());
                    touch(document, now);
                    event(
                            actor,
                            document,
                            restore ? "RESTORED" : PublicationStatusEnum.DISABLED.getValue(),
                            null,
                            before,
                            now);
                    return view(document);
                },
                () -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteDraft(IdentityDTO actor, IdVersionForm form) {
        KnowledgeRules.administrator(actor);
        var snapshot = baseMapper.selectById(form.getId());
        version(snapshot, form.getRowVersion());
        var attempts =
                indexJobMapper.selectList(
                        new LambdaQueryWrapper<IndexJobEntity>()
                                .eq(IndexJobEntity::getDocumentId, snapshot.getId()));
        for (var job : attempts)
            if (!IndexTransactionStateEnum.ABORTED.getValue().equals(job.getSubmitState())) {
                var status = aiIndexClient.status(job.getId());
                if (!IndexTransactionStateEnum.COMMITTED.getValue().equals(job.getCancelState())
                        || status == null
                        || !job.getRequestHash().equals(status.getRequestHash())
                        || !IndexCommandTypeEnum.CANCEL.getValue().equals(status.getLastCommand())
                        || !Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(status.getStatus())
                        || !Set.of("NOT_REQUIRED", "NOT_SUBMITTED", "SETTLED")
                        .contains(status.getParserState()))
                    throw new BusinessException(CommonErrorCode.CONFLICT);
            }
        businessTransaction.execute(
                () -> {
                    var document = baseMapper.lock(form.getId());
                    version(document, form.getRowVersion());
                    if (!PublicationStatusEnum.DRAFT
                            .getValue()
                            .equals(document.getPublicationStatus())
                            || document.getPublishedAt() != null)
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    for (var attempt : attempts) {
                        var locked = indexJobMapper.lock(attempt.getId());
                        if (!Objects.equals(locked.getSubmitState(), attempt.getSubmitState())
                                || !Objects.equals(
                                locked.getCancelState(), attempt.getCancelState()))
                            throw new BusinessException(CommonErrorCode.CONFLICT);
                    }
                    var before = view(document);
                    LocalDateTime now = baseMapper.decisionTime();
                    document.setPublicationStatus(PublicationStatusEnum.DELETED.getValue());
                    document.setDeletedAt(now);
                    touch(document, now);
                    event(
                            actor,
                            document,
                            PublicationStatusEnum.DELETED.getValue(),
                            null,
                            before,
                            now);
                    return true;
                },
                () -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<DocumentEventVO> events(IdentityDTO actor, KnowledgeQueryForm form) {
        KnowledgeRules.administrator(actor);
        if (form.getDocumentId() == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        long total = documentEventMapper.countEvents(form.getDocumentId());
        var records = documentEventMapper.listEvents(form.getDocumentId(), form.getPageSize(), form.offset())
                .stream().map(KnowledgeViews::of).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * 发布前查询真实AI成功与当前副本可见性，不能在锁内外调。
     */
    private void ready(DocumentEntity document) {
        if (document.getSourceReviewNote() == null
                || document.getSourceReviewNote().isBlank()
                || document.getCurrentJobId() == null
                || documentRuleMapper.selectCount(
                new LambdaQueryWrapper<DocumentRuleEntity>()
                        .eq(DocumentRuleEntity::getDocumentId, document.getId()))
                == 0) throw new BusinessException(DocumentErrorCode.PUBLICATION_NOT_READY);
        var job = indexJobMapper.selectById(document.getCurrentJobId());
        if (job == null) throw new BusinessException(DocumentErrorCode.PUBLICATION_NOT_READY);
        var remote = aiIndexClient.status(job.getId());
        if (remote == null
                || !remote.getRequestHash().equals(job.getRequestHash())
                || !IndexCommandTypeEnum.SUBMIT.getValue().equals(remote.getLastCommand())
                || !"SUCCEEDED".equals(remote.getStatus())
                || !remote.getReplicasVerified()
                || remote.getChunkCount() < 1
                || !Set.of("NOT_REQUIRED", "SETTLED").contains(remote.getParserState()))
            throw new BusinessException(DocumentErrorCode.PUBLICATION_NOT_READY);
    }

    /**
     * 文档锁后按同序锁当前任务，取消准备和取消提交均阻止发布。
     */
    private void localReady(DocumentEntity document) {
        var job = indexJobMapper.lock(document.getCurrentJobId());
        if (job == null
                || !IndexTransactionStateEnum.COMMITTED.getValue().equals(job.getSubmitState())
                || !Set.of(
                        IndexTransactionStateEnum.NONE.getValue(),
                        IndexTransactionStateEnum.ABORTED.getValue())
                .contains(job.getCancelState()))
            throw new BusinessException(DocumentErrorCode.PUBLICATION_NOT_READY);
    }

    /**
     * 同上传者原键同义返回，异义必须拒绝。
     */
    private DocumentEntity uploadReceipt(IdentityDTO actor, String key, String hash) {
        var document =
                baseMapper.selectOne(
                        new LambdaQueryWrapper<DocumentEntity>()
                                .eq(DocumentEntity::getCreatedBy, actor.getOwnerId())
                                .eq(DocumentEntity::getClientUploadId, key));
        if (document != null && hash != null && !hash.equals(document.getUploadRequestHash()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return document;
    }

    /**
     * 规范规则并核验外部目录，重复规则拒绝，不扩大NULL语义。
     */
    private void validateRules(IdentityDTO actor, List<DocumentRuleForm> values) {
        normalizeRules(values);
        var projects = new HashSet<String>();
        var models = new HashSet<String>();
        for (var value : values) {
            projects.add(value.getServiceItemId());
            if (value.getProductModelId() != null) models.add(value.getProductModelId());
        }
        supportVerificationClient.catalog(actor, projects, false);
        supportVerificationClient.catalog(actor, models, true);
    }

    /**
     * 原请求摘要规范化不依赖目录当前状态，旧回执先于新增资格复核。
     */
    private void normalizeRules(List<DocumentRuleForm> values) {
        if (values == null || values.isEmpty() || values.size() > 50)
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        var seen = new HashSet<String>();
        for (var value : values) {
            KnowledgeRules.normalizeRule(value);
            if (!seen.add(knowledgeDigest.hash(value)))
                throw new BusinessException(CommonErrorCode.DUPLICATE_VALUE);
        }
    }

    /**
     * 草稿规则与元数据处于同一本地事务。
     */
    private void replaceRules(String documentId, List<DocumentRuleForm> values, LocalDateTime now) {
        documentRuleMapper.delete(
                new LambdaQueryWrapper<DocumentRuleEntity>()
                        .eq(DocumentRuleEntity::getDocumentId, documentId));
        int number = 0;
        for (var form : values) {
            var rule = new DocumentRuleEntity();
            rule.setId(id());
            rule.setDocumentId(documentId);
            rule.setRuleNumber(++number);
            rule.setServiceItemId(form.getServiceItemId());
            rule.setProductModelId(form.getProductModelId());
            rule.setOsName(form.getOsName());
            rule.setOsVersion(form.getOsVersion());
            rule.setSoftwareName(form.getSoftwareName());
            rule.setSoftwareVersion(form.getSoftwareVersion());
            rule.setCreatedAt(now);
            documentRuleMapper.insert(rule);
        }
    }

    /**
     * 原件保留真实字节，HTML与远程资源不进入可显示知识。
     */
    private void validateMarkdown(byte[] bytes) {
        try {
            String text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
            if (text.isBlank()
                    || text.indexOf(0) >= 0
                    || Pattern.compile(
                            "<\\s*(?:/?[a-zA-Z]|!--)|!\\[[^\\]]*\\]\\(\\s*(?:https?:|//|data:)",
                            Pattern.CASE_INSENSITIVE)
                    .matcher(text)
                    .find()) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        } catch (CharacterCodingException malformed) {
            throw new BusinessException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    /**
     * 版本冲突不覆盖新草稿。
     */
    private void version(DocumentEntity document, long expected) {
        if (document == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (document.getRowVersion() != expected)
            throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 更新显式递增文档版本。
     */
    private void touch(DocumentEntity document, LocalDateTime now) {
        document.setRowVersion(document.getRowVersion() + 1);
        document.setUpdatedAt(now);
        baseMapper.updateById(document);
    }

    /**
     * 只在调用者事务内追加不可变事件。
     */
    private void event(
            IdentityDTO actor,
            DocumentEntity document,
            String type,
            String related,
            DocumentVO before,
            LocalDateTime now) {
        var details = objectMapper.createObjectNode().put("schema_version", 1);
        details.set("before", objectMapper.valueToTree(before));
        details.set("after", objectMapper.valueToTree(view(document)));
        var event = new DocumentEventEntity();
        event.setId(id());
        event.setDocumentId(document.getId());
        event.setActorId(actor.getOwnerId());
        event.setEventType(type);
        event.setRelatedDocumentId(related);
        event.setDetails(details);
        event.setCreatedAt(now);
        documentEventMapper.insert(event);
    }

    /**
     * 规则在管理详情中明确展示，不暴露存储字段。
     */
    private DocumentVO view(DocumentEntity document) {
        var result = KnowledgeViews.of(document);
        result.setRules(
                documentRuleMapper
                        .selectList(
                                new LambdaQueryWrapper<DocumentRuleEntity>()
                                        .eq(DocumentRuleEntity::getDocumentId, document.getId())
                                        .orderByAsc(DocumentRuleEntity::getRuleNumber))
                        .stream()
                        .map(
                                rule -> {
                                    var form = new DocumentRuleForm();
                                    form.setServiceItemId(rule.getServiceItemId());
                                    form.setProductModelId(rule.getProductModelId());
                                    form.setOsName(rule.getOsName());
                                    form.setOsVersion(rule.getOsVersion());
                                    form.setSoftwareName(rule.getSoftwareName());
                                    form.setSoftwareVersion(rule.getSoftwareVersion());
                                    return form;
                                })
                        .toList());
        return result;
    }


    /**
     * 所属后端生成业务标识。
     */
    private String id() {
        return IdentifierGenerator.next();
    }
}
