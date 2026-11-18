package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.ticket.component.ActionDraftClient;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.ticket.component.RequestDigest;
import com.yangaobo.servicedesk.ticket.error.DeviceErrorCode;
import com.yangaobo.servicedesk.ticket.error.TicketErrorCode;
import com.yangaobo.servicedesk.ticket.mapper.DeviceMapper;
import com.yangaobo.servicedesk.ticket.mapper.ProductModelMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketEventMapper;
import com.yangaobo.servicedesk.ticket.mapper.TicketMapper;
import com.yangaobo.servicedesk.ticket.model.dto.support.ServiceAdmission;
import com.yangaobo.servicedesk.ticket.model.dto.support.DraftValidation;
import com.yangaobo.servicedesk.ticket.model.entity.DeviceEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ActionReceiptEntity;
import com.yangaobo.servicedesk.ticket.model.enums.TicketActionTypeEnum;
import com.yangaobo.servicedesk.ticket.service.IActionReceiptService;
import com.yangaobo.servicedesk.ticket.model.entity.ProductModelEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;
import com.yangaobo.servicedesk.ticket.model.enums.ClosureReasonEnum;
import com.yangaobo.servicedesk.ticket.model.enums.ResolutionTypeEnum;
import com.yangaobo.servicedesk.ticket.model.enums.TicketPriorityEnum;
import com.yangaobo.servicedesk.ticket.model.enums.TicketStatusEnum;
import com.yangaobo.servicedesk.ticket.model.enums.VisibilityEnum;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportContextForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.OnsiteEligibilityForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketAssignmentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketCommentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketContentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketWaitForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketInformationForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketContextForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketPriorityForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketQueryForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketResolveForm;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketDetailVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketEventVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketVO;
import com.yangaobo.servicedesk.ticket.service.IServiceEntitlementService;
import com.yangaobo.servicedesk.ticket.service.ITicketDeadlineService;
import com.yangaobo.servicedesk.ticket.service.ITicketEventService;
import com.yangaobo.servicedesk.ticket.service.ITicketService;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;
import com.yangaobo.servicedesk.ticket.service.TicketScope;
import com.yangaobo.servicedesk.ticket.service.TicketStateWriter;
import com.yangaobo.servicedesk.ticket.service.TicketViews;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 维护工单受理、人工动作、时间线与通知的本地原子性。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketServiceImpl extends ServiceImpl<TicketMapper, TicketEntity>
        implements ITicketService {
    private final TicketEventMapper eventMapper;
    private final ITicketEventService timelineService;
    private final IServiceEntitlementService entitlementService;
    private final DeviceMapper deviceMapper;
    private final ProductModelMapper modelMapper;
    private final RequestDigest digest;
    private final BusinessTransaction transactions;
    private final ObjectMapper objectMapper;
    private final IdentityVerificationClient identityClient;
    private final ActionDraftClient draftClient;
    private final IActionReceiptService receiptService;
    private final ITicketDeadlineService ticketDeadlineService;
    private final TicketStateWriter stateWriter;

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketVO create(IdentityDTO actor, TicketCreateForm form) {
        SupportAuthorization.role(actor, "CUSTOMER_USER");
        String hash = digest.actionHash("CREATE", actor.getOrganizationId(), form);
        TicketEntity old = creationReceipt(actor, form.getClientRequestId(), form.getSourceDraftRootId(), hash);
        if (old != null) return TicketViews.of(old);
        DraftValidation validation =
                draftClient.validate(
                        actor,
                        form.getSourceDraftId(),
                        "CREATE_TICKET",
                        actor.getOrganizationId(),
                        form.getServiceItemId(),
                        form.getDeviceId(),
                        null,
                        null, objectMapper.valueToTree(form));
        JsonNode context = context(form.getSupportContext(), "USER_REPORT");
        return transactions.execute(
                () -> {
                    TicketEntity replay = creationReceipt(actor, form.getClientRequestId(), form.getSourceDraftRootId(), hash);
                    if (replay != null) return TicketViews.of(replay);
                    ServiceAdmission admission =
                            entitlementService.admit(
                                    actor.getOrganizationId(), form.getServiceItemId());
                    DeviceEntity device =
                            form.getDeviceId() == null
                                    ? null
                                    : deviceMapper.lock(form.getDeviceId());
                    if (form.getDeviceId() != null
                            && (device == null
                            || !actor.getOrganizationId().equals(device.getCustomerId())
                            || !"ACTIVE".equals(device.getStatus())))
                        throw new BusinessException(DeviceErrorCode.DEVICE_INELIGIBLE);
                    LocalDateTime now = baseMapper.decisionTime();
                    draftClient.requireValid(validation, now);
                    TicketEntity ticket = new TicketEntity();
                    ticket.setId(id());
                    ticket.setTicketNo("SD" + ticket.getId());
                    ticket.setCustomerId(actor.getOrganizationId());
                    ticket.setRequesterId(actor.getOwnerId());
                    ticket.setClientRequestId(form.getClientRequestId());
                    ticket.setRequestHash(hash);
                    ticket.setSourceDraftId(form.getSourceDraftId());
                    ticket.setHandoverSnapshot(validation == null ? null : validation.getHandover());
                    ticket.setServiceItemId(form.getServiceItemId());
                    ticket.setEntitlementId(
                            form.getServiceItemId() == null
                                    ? null
                                    : admission.getEntitlement().getId());
                    ticket.setServiceScopeSnapshot(
                            scope(admission, form.getServiceItemId() == null));
                    ticket.setDeviceId(form.getDeviceId());
                    ticket.setDeviceSnapshot(device == null ? null : deviceSnapshot(device));
                    ticket.setSupportContext(context);
                    ticket.setServiceAreaCode(form.getServiceAreaCode());
                    ticket.setServiceLocation(form.getServiceLocation());
                    ticket.setTitle(form.getTitle());
                    ticket.setSymptom(form.getSymptom());
                    ticket.setAttemptedActions(form.getAttemptedActions());
                    ticket.setPriority(TicketPriorityEnum.NORMAL.getValue());
                    ticket.setStatus(TicketStatusEnum.SUBMITTED.getValue());
                    ticket.setOnsiteEligible(false);
                    ticket.setRowVersion(0L);
                    ticket.setDiagnosisContextVersion(0L);
                    ticket.setEventSequence(0L);
                    ticket.setWaitCycle(0);
                    ticket.setCreatedAt(now);
                    ticket.setUpdatedAt(now);
                    ticket.setAcceptanceDueAt(now.plusHours(4));
                    baseMapper.insert(ticket);
                    TicketEventEntity event = append(
                            ticket,
                            actor,
                            "CREATED",
                            null,
                            null,
                            null,
                            VisibilityEnum.PUBLIC.getValue(),
                            form.getSymptom(),
                            objectMapper.createObjectNode(),
                            now);
                    draftClient.requireValid(validation, baseMapper.decisionTime());
                    receiptService.record(actor, ticket, null, event, "CREATE_TICKET", form.getClientRequestId(),
                            hash, form.getSourceDraftId(), validation == null ? null : validation.getDraftRootId(), now);
                    return TicketViews.of(ticket);
                },
                () -> {
                    TicketEntity replay = creationReceipt(actor, form.getClientRequestId(), form.getSourceDraftRootId(), hash);
                    return replay == null ? null : TicketViews.of(replay);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<TicketVO> list(IdentityDTO actor, TicketQueryForm form) {
        TicketScope.requireReader(actor);
        boolean customer = actor.getRoles().contains("CUSTOMER_USER");
        LocalDateTime overdueAt = Boolean.TRUE.equals(form.getOverdue()) ? baseMapper.decisionTime() : null;
        long total = baseMapper.countTickets(actor, customer, form, overdueAt);
        var records = baseMapper.listTickets(actor, customer, form, overdueAt, form.getPageSize(), form.offset())
                .stream().map(TicketViews::of).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketDetailVO query(IdentityDTO actor, String id) {
        TicketEntity ticket = readable(actor, baseMapper.selectById(id));
        TicketDetailVO result = new TicketDetailVO();
        result.setTicket(TicketViews.of(ticket));
        result.setEvents(eventMapper.listReadable(id, actor.getRoles().contains("CUSTOMER_USER"), 100, 0)
                .stream().map(TicketViews::of).toList());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<TicketEventVO> events(IdentityDTO actor, String ticketId, BasePageForm form) {
        readable(actor, baseMapper.selectById(ticketId));
        boolean customer = actor.getRoles().contains("CUSTOMER_USER");
        long total = eventMapper.countReadable(ticketId, customer);
        var records = eventMapper.listReadable(ticketId, customer, form.getPageSize(), form.offset())
                .stream().map(TicketViews::of).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketVO byRequest(IdentityDTO actor, String key) {
        identifier(key);
        TicketEntity ticket =
                baseMapper.selectOne(
                        new LambdaQueryWrapper<TicketEntity>()
                                .eq(TicketEntity::getRequesterId, actor.getOwnerId())
                                .eq(TicketEntity::getClientRequestId, key));
        return TicketViews.of(readable(actor, ticket));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO actionReceipt(IdentityDTO actor, String ticketId, String key) {
        readable(actor, baseMapper.selectById(ticketId));
        identifier(key);
        TicketActionReceiptVO result = receiptService.byAction(actor, key);
        if (!ticketId.equals(result.getTicketId())) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketVO updateContext(IdentityDTO actor, String id, TicketContextForm form) {
        mutate(
                actor,
                id,
                form,
                "CONTEXT_UPDATED",
                form,
                VisibilityEnum.PUBLIC.getValue(),
                form.getReason(),
                null,
                (ticket, now, metadata) -> {
                    handlerOrAgent(actor, ticket);
                    active(ticket);
                    noBooking(ticket);
                    if (ticket.getServiceItemId() != null && form.getServiceItemId() == null)
                        throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                    metadata.set("old_scope", ticket.getServiceScopeSnapshot());
                    metadata.set("old_context", ticket.getSupportContext());
                    if (!Objects.equals(ticket.getServiceItemId(), form.getServiceItemId())) {
                        ServiceAdmission admission =
                                entitlementService.admit(
                                        ticket.getCustomerId(), form.getServiceItemId());
                        ticket.setServiceItemId(form.getServiceItemId());
                        ticket.setEntitlementId(admission.getEntitlement().getId());
                        ticket.setServiceScopeSnapshot(scope(admission, false));
                    }
                    JsonNode nextContext = context(form.getSupportContext(), "HUMAN_RECORD");
                    if (!Objects.equals(metadata.get("old_scope"), ticket.getServiceScopeSnapshot())
                            || diagnosticEnvironmentChanged(ticket.getSupportContext(), nextContext)) {
                        ticket.setDiagnosisContextVersion(Math.addExact(ticket.getDiagnosisContextVersion(), 1L));
                    }
                    ticket.setSupportContext(nextContext);
                    ticket.setServiceAreaCode(form.getServiceAreaCode());
                    ticket.setServiceLocation(form.getServiceLocation());
                    metadata.set("new_scope", ticket.getServiceScopeSnapshot());
                    metadata.set("new_context", ticket.getSupportContext());
                });
        return query(actor, id).getTicket();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO onsiteEligibility(
            IdentityDTO actor, String id, OnsiteEligibilityForm form) {
        return mutate(
                actor,
                id,
                form,
                "ONSITE_ELIGIBILITY_CHANGED",
                form,
                "PUBLIC",
                form.getReason(),
                null,
                (ticket, now, metadata) -> {
                    handlerOrAgent(actor, ticket);
                    active(ticket);
                    noBooking(ticket);
                    if (form.getOnsiteEligible()
                            && (!Set.of("PROCESSING", "WAITING_CUSTOMER").contains(ticket.getStatus())
                            || ticket.getServiceItemId() == null
                            || !ticket.getServiceScopeSnapshot()
                            .path("onsite_supported")
                            .asBoolean()
                            || ticket.getServiceAreaCode() == null
                            || ticket.getServiceLocation() == null
                            || ticket.getServiceLocation().isBlank()))
                        throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                    ticket.setOnsiteEligible(form.getOnsiteEligible());
                    metadata.put("onsite_eligible", form.getOnsiteEligible());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO accept(IdentityDTO actor, String id, TicketActionForm form) {
        return mutate(
                actor,
                id,
                form,
                "ACCEPTED",
                form,
                VisibilityEnum.PUBLIC.getValue(),
                "技术人员已接单",
                null,
                (ticket, now, metadata) -> {
                    SupportAuthorization.role(actor, "TECHNICIAN");
                    state(ticket, TicketStatusEnum.SUBMITTED.getValue());
                    ticketDeadlineService.recordAcceptanceOverdue(ticket, now);
                    ticket.setAssigneeId(actor.getOwnerId());
                    ticket.setFirstAcceptedAt(now);
                    ticket.setStatus(TicketStatusEnum.PROCESSING.getValue());
                    metadata.put("assignee_id", actor.getOwnerId());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO assign(
            IdentityDTO actor, String id, TicketAssignmentForm form, boolean reassign) {
        String type = reassign ? "REASSIGNED" : "ASSIGNED";
        String hash = digest.actionHash(type, id, form);
        TicketEntity initial = readable(actor, baseMapper.selectById(id));
        SupportAuthorization.agent(actor, initial.getCustomerId());
        ActionReceiptEntity replay = replayAction(actor, form.getClientActionId(),
                objectMapper.valueToTree(form).path("sourceDraftRootId").asText(null), hash);
        if (replay != null) return receiptService.view(replay);
        if (!identityClient
                .eligibility(actor, initial.getCustomerId(), form.getAssigneeId())
                .isAssigneeAvailable())
            throw new BusinessException(TicketErrorCode.ASSIGNEE_UNAVAILABLE);
        return mutate(
                actor,
                id,
                form,
                type,
                form,
                VisibilityEnum.PUBLIC.getValue(),
                form.getReason(),
                null,
                (ticket, now, metadata) -> {
                    SupportAuthorization.agent(actor, ticket.getCustomerId());
                    if (reassign) {
                        if (!Set.of(
                                        TicketStatusEnum.PROCESSING.getValue(),
                                        TicketStatusEnum.WAITING_CUSTOMER.getValue(),
                                        TicketStatusEnum.WAITING_EXTERNAL.getValue())
                                .contains(ticket.getStatus())
                                || form.getAssigneeId().equals(ticket.getAssigneeId()))
                            throw new BusinessException(CommonErrorCode.CONFLICT);
                    } else state(ticket, TicketStatusEnum.SUBMITTED.getValue());
                    metadata.put("previous_assignee_id", ticket.getAssigneeId());
                    metadata.put("assignee_id", form.getAssigneeId());
                    if (ticket.getFirstAcceptedAt() == null) {
                        ticketDeadlineService.recordAcceptanceOverdue(ticket, now);
                        ticket.setFirstAcceptedAt(now);
                        ticket.setStatus(TicketStatusEnum.PROCESSING.getValue());
                    }
                    ticket.setAssigneeId(form.getAssigneeId());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO priority(IdentityDTO actor, String id, TicketPriorityForm form) {
        return mutate(
                actor,
                id,
                form,
                "PRIORITY_CHANGED",
                form,
                VisibilityEnum.INTERNAL.getValue(),
                form.getReason(),
                null,
                (ticket, now, metadata) -> {
                    SupportAuthorization.agent(actor, ticket.getCustomerId());
                    state(ticket, TicketStatusEnum.SUBMITTED.getValue());
                    metadata.put("old_priority", ticket.getPriority());
                    metadata.put("new_priority", form.getPriority());
                    ticket.setPriority(form.getPriority());
                    ticket.setAcceptanceDueAt(
                            ticket.getCreatedAt()
                                    .plusHours(
                                            TicketPriorityEnum.HIGH
                                                    .getValue()
                                                    .equals(form.getPriority())
                                                    ? 1
                                                    : 4));
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO requestInformation(
            IdentityDTO actor, String id, TicketInformationForm form) {
        ActionReceiptEntity original = replayAction(actor, form.getClientActionId(), form.getSourceDraftRootId(),
                digest.actionHash("NEED_INFO", id, form));
        if (original != null) return receiptService.view(original);
        DraftValidation validation = validateDraftAction(actor, id, form, form.getSourceDraftId(), "REQUEST_INFORMATION");
        return mutate(
                actor,
                id,
                form,
                "NEED_INFO",
                form,
                VisibilityEnum.PUBLIC.getValue(),
                form.getContent(),
                validation,
                (ticket, now, metadata) -> {
                    handler(actor, ticket);
                    state(ticket, TicketStatusEnum.PROCESSING.getValue());
                    ticket.setStatus(TicketStatusEnum.WAITING_CUSTOMER.getValue());
                    scheduleFollowUp(ticket, form, now, metadata);
                    if (validation != null) {
                        metadata.put("source_draft_id", form.getSourceDraftId());
                        metadata.put("source_draft_root_id", validation.getDraftRootId());
                    }
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO supplement(IdentityDTO actor, String id, TicketContentForm form) {
        return mutate(
                actor,
                id,
                form,
                "SUPPLEMENTED",
                form,
                VisibilityEnum.PUBLIC.getValue(),
                form.getContent(),
                null,
                (ticket, now, metadata) -> {
                    requester(actor, ticket);
                    state(ticket, TicketStatusEnum.WAITING_CUSTOMER.getValue());
                    clearWait(ticket);
                    ticket.setStatus(TicketStatusEnum.PROCESSING.getValue());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO comment(IdentityDTO actor, String id, TicketCommentForm form) {
        ActionReceiptEntity original = replayAction(actor, form.getClientActionId(), form.getSourceDraftRootId(),
                digest.actionHash("COMMENTED", id, form));
        if (original != null) return receiptService.view(original);
        DraftValidation validation = validateDraftAction(actor, id, form, form.getSourceDraftId(), "ADD_RECORD");
        return mutate(
                actor,
                id,
                form,
                "COMMENTED",
                form,
                form.getVisibility(),
                form.getContent(),
                validation,
                (ticket, now, metadata) -> {
                    handlerOrAgent(actor, ticket);
                    active(ticket);
                    draftClient.requireValid(validation, now);
                    if (validation != null) {
                        metadata.put("source_draft_id", form.getSourceDraftId());
                        metadata.put("source_draft_root_id", validation.getDraftRootId());
                    }
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO resolve(IdentityDTO actor, String id, TicketResolveForm form) {
        ActionReceiptEntity original = replayAction(actor, form.getClientActionId(), form.getSourceDraftRootId(),
                digest.actionHash("RESOLVED", id, form));
        if (original != null) return receiptService.view(original);
        DraftValidation validation = validateDraftAction(actor, id, form, form.getSourceDraftId(), "RESOLVE_TICKET");
        return mutate(
                actor,
                id,
                form,
                TicketStatusEnum.RESOLVED.getValue(),
                form,
                VisibilityEnum.PUBLIC.getValue(),
                form.getContent(),
                validation,
                (ticket, now, metadata) -> {
                    handler(actor, ticket);
                    state(ticket, TicketStatusEnum.PROCESSING.getValue());
                    noBooking(ticket);
                    draftClient.requireValid(validation, now);
                    if (validation != null) {
                        metadata.put("source_draft_id", form.getSourceDraftId());
                        metadata.put("source_draft_root_id", validation.getDraftRootId());
                    }
                    if (ticket.getServiceItemId() == null
                            && !ResolutionTypeEnum.REFERRED
                            .getValue()
                            .equals(form.getResolutionType()))
                        throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                    if (ResolutionTypeEnum.REFERRED.getValue().equals(form.getResolutionType())
                            && (form.getReferralTarget() == null
                            || form.getReferralTarget().isBlank()))
                        throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                    ticket.setStatus(TicketStatusEnum.RESOLVED.getValue());
                    ticket.setResolutionType(form.getResolutionType());
                    ticket.setResolvedAt(now);
                    ticket.setConfirmationDueAt(now.plusHours(72));
                    ticket.setOnsiteEligible(false);
                    metadata.put("resolution_type", form.getResolutionType());
                    metadata.put("referral_target", form.getReferralTarget());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO confirm(IdentityDTO actor, String id, TicketActionForm form) {
        return mutate(
                actor,
                id,
                form,
                TicketStatusEnum.CLOSED.getValue(),
                form,
                VisibilityEnum.PUBLIC.getValue(),
                "客户确认处理结果",
                null,
                (ticket, now, metadata) -> {
                    requester(actor, ticket);
                    state(ticket, TicketStatusEnum.RESOLVED.getValue());
                    ticket.setStatus(TicketStatusEnum.CLOSED.getValue());
                    ticket.setClosureReason(ClosureReasonEnum.CUSTOMER_CONFIRMED.getValue());
                    ticket.setClosedAt(now);
                    metadata.put("closure_reason", ClosureReasonEnum.CUSTOMER_CONFIRMED.getValue());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO unresolved(IdentityDTO actor, String id, TicketContentForm form) {
        return mutate(
                actor,
                id,
                form,
                "UNRESOLVED",
                form,
                VisibilityEnum.PUBLIC.getValue(),
                form.getContent(),
                null,
                (ticket, now, metadata) -> {
                    requester(actor, ticket);
                    state(ticket, TicketStatusEnum.RESOLVED.getValue());
                    metadata.put("previous_resolution_type", ticket.getResolutionType());
                    ticket.setStatus(TicketStatusEnum.PROCESSING.getValue());
                    ticket.setResolutionType(null);
                    ticket.setResolvedAt(null);
                    ticket.setConfirmationDueAt(null);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO cancel(IdentityDTO actor, String id, TicketContentForm form) {
        return mutate(
                actor,
                id,
                form,
                TicketStatusEnum.CANCELLED.getValue(),
                form,
                VisibilityEnum.PUBLIC.getValue(),
                form.getContent(),
                null,
                (ticket, now, metadata) -> {
                    active(ticket);
                    noBooking(ticket);
                    if (!actor.getOwnerId().equals(ticket.getRequesterId()))
                        handlerOrAgent(actor, ticket);
                    clearWait(ticket);
                    ticket.setStatus(TicketStatusEnum.CANCELLED.getValue());
                    ticket.setCancelledAt(now);
                    ticket.setOnsiteEligible(false);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO waitExternal(IdentityDTO actor, String id, TicketWaitForm form) {
        return mutate(actor, id, form, "WAIT_EXTERNAL", form, VisibilityEnum.PUBLIC.getValue(),
                form.getContent(), null, (ticket, now, metadata) -> {
                    handler(actor, ticket);
                    state(ticket, TicketStatusEnum.PROCESSING.getValue());
                    noBooking(ticket);
                    ticket.setOnsiteEligible(false);
                    ticket.setStatus(TicketStatusEnum.WAITING_EXTERNAL.getValue());
                    scheduleFollowUp(ticket, form, now, metadata);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO endWait(IdentityDTO actor, String id, TicketContentForm form) {
        return mutate(actor, id, form, "END_WAIT", form, VisibilityEnum.PUBLIC.getValue(),
                form.getContent(), null, (ticket, now, metadata) -> {
                    handler(actor, ticket);
                    waiting(ticket);
                    metadata.put("wait_cycle", ticket.getWaitCycle());
                    clearWait(ticket);
                    ticket.setStatus(TicketStatusEnum.PROCESSING.getValue());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO followUp(IdentityDTO actor, String id, TicketWaitForm form) {
        return mutate(actor, id, form, "FOLLOW_UP", form, VisibilityEnum.PUBLIC.getValue(),
                form.getContent(), null, (ticket, now, metadata) -> {
                    handler(actor, ticket);
                    waiting(ticket);
                    scheduleFollowUp(ticket, form, now, metadata);
                });
    }

    /**
     * 仅两种等待可结束或续期，不把备注当成恢复处理。
     */
    private void waiting(TicketEntity ticket) {
        if (!Set.of("WAITING_CUSTOMER", "WAITING_EXTERNAL").contains(ticket.getStatus()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 同事务登记新轮次和明确期限；已过期时刻不能制造一个立即逾期的新轮次。
     */
    private void scheduleFollowUp(TicketEntity ticket, TicketWaitForm form, LocalDateTime now, ObjectNode metadata) {
        LocalDateTime deadline = LocalDateTime.ofInstant(form.getFollowUpAt(), ZoneOffset.UTC);
        if (!deadline.isAfter(now)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        ticket.setWaitReason(form.getContent());
        ticket.setFollowUpAt(deadline);
        ticket.setFollowUpNotifiedAt(null);
        ticket.setWaitCycle(Math.addExact(ticket.getWaitCycle(), 1));
        metadata.put("wait_cycle", ticket.getWaitCycle());
        metadata.put("follow_up_at", form.getFollowUpAt().toString());
    }

    /**
     * 离开等待清空当前期限，历史轮次和原因仍保留在不可变事件。
     */
    private void clearWait(TicketEntity ticket) {
        ticket.setWaitReason(null);
        ticket.setFollowUpAt(null);
        ticket.setFollowUpNotifiedAt(null);
    }

    /**
     * 人工动作统一按工单行锁串行化，回执先于版本与新动作校验。
     */
    private TicketActionReceiptVO mutate(
            IdentityDTO actor,
            String id,
            TicketActionForm form,
            String type,
            Object input,
            String visibility,
            String content,
            DraftValidation validation,
            Change change) {
        identifier(id);
        String hash = digest.actionHash(type, id, input);
        return transactions.execute(
                () -> {
                    TicketEntity ticket = readable(actor, baseMapper.lock(id));
                    ActionReceiptEntity replay = replayAction(actor, form.getClientActionId(),
                            objectMapper.valueToTree(form).path("sourceDraftRootId").asText(null), hash);
                    if (replay != null) return receiptService.view(replay);
                    if (!ticket.getRowVersion().equals(form.getRowVersion()))
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    String from = ticket.getStatus();
                    LocalDateTime now = baseMapper.decisionTime();
                    ObjectNode metadata = objectMapper.createObjectNode();
                    change.apply(ticket, now, metadata);
                    stateWriter.save(ticket, now);
                    TicketEventEntity event = append(ticket, actor, type, form.getClientActionId(), hash,
                            from, visibility, content, metadata, now);
                    draftClient.requireValid(validation, baseMapper.decisionTime());
                    return receiptService.view(receiptService.record(actor, ticket, null, event,
                            TicketActionTypeEnum.fromEvent(type).getValue(), form.getClientActionId(), hash,
                            metadata.path("source_draft_id").asText(null),
                            metadata.path("source_draft_root_id").asText(null), now));
                },
                () -> {
                    readable(actor, baseMapper.selectById(id));
                    ActionReceiptEntity old = replayAction(actor, form.getClientActionId(),
                            objectMapper.valueToTree(form).path("sourceDraftRootId").asText(null), hash);
                    return old == null ? null : receiptService.view(old);
                });
    }

    /**
     * 状态、时间线和明确收件人通知共享本地事务。
     */
    private TicketEventEntity append(
            TicketEntity ticket,
            IdentityDTO actor,
            String type,
            String key,
            String hash,
            String from,
            String visibility,
            String content,
            ObjectNode metadata,
            LocalDateTime now) {
        return timelineService.append(
                ticket, actor, type, key, hash, from, visibility, content, metadata, now, null);
    }

    /**
     * 原建单键在当前本人及原组织权限内读取。
     */
    private TicketEntity creationReceipt(IdentityDTO actor, String key, String root, String hash) {
        ActionReceiptEntity receipt = replayAction(actor, key, root, hash);
        return receipt == null ? null : readable(actor, baseMapper.selectById(receipt.getTicketId()));
    }

    /**
     * 固定环境采用具名字段及来源，未知保留空值。
     */
    private JsonNode context(SupportContextForm form, String source) {
        if (form.getOsVersion() != null && (form.getOsName() == null || form.getOsName().isBlank())
                || form.getSoftwareVersion() != null
                && (form.getSoftwareName() == null || form.getSoftwareName().isBlank()))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        ObjectNode data = objectMapper.createObjectNode();
        data.put("schema_version", 1);
        data.put("os_name", form.getOsName());
        data.put("os_version", form.getOsVersion());
        data.put("software_name", form.getSoftwareName());
        data.put("software_version", form.getSoftwareVersion());
        data.put("connection_type", form.getConnectionType());
        data.put("error_code", form.getErrorCode());
        data.put("affected_scope", form.getAffectedScope());
        ObjectNode sources = data.putObject("field_sources");
        for (String key :
                List.of(
                        "os_name",
                        "os_version",
                        "software_name",
                        "software_version",
                        "connection_type",
                        "error_code",
                        "affected_scope"))
            if (!data.get(key).isNull() && !"UNKNOWN".equals(data.get(key).asText()))
                sources.put(key, source);
        return data;
    }

    /**
     * 来源说明和现场地址不冒充诊断环境变化，只比较明确的环境字段。
     */
    private boolean diagnosticEnvironmentChanged(JsonNode previous, JsonNode next) {
        for (String field : List.of("os_name", "os_version", "software_name", "software_version",
                "connection_type", "affected_scope", "error_code")) {
            if (!Objects.equals(previous.path(field), next.path(field))) return true;
        }
        return false;
    }

    /**
     * 受理快照不随未来目录或资格修改而回写。
     */
    private JsonNode scope(ServiceAdmission admission, boolean triage) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("schema_version", 1);
        result.put("admission_type", triage ? "TRIAGE" : "COVERED");
        result.put(
                triage ? "admission_entitlement_id" : "entitlement_id",
                admission.getEntitlement().getId());
        result.put("qualification_version", admission.getEntitlement().getRowVersion().toString());
        result.put("starts_on", admission.getEntitlement().getStartsOn().toString());
        result.put("ends_on", admission.getEntitlement().getEndsOn().toString());
        if (!triage) {
            result.put("service_item_id", admission.getItem().getId());
            result.put("support_scope", admission.getItem().getSupportScope());
            result.put("onsite_supported", admission.getItem().getOnsiteSupported());
        }
        return result;
    }

    /**
     * 设备快照不复制敏感序列号。
     */
    private JsonNode deviceSnapshot(DeviceEntity device) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("schema_version", 1);
        result.put("asset_code", device.getAssetCode());
        result.put("product_model_id", device.getProductModelId());
        ProductModelEntity model = modelMapper.selectById(device.getProductModelId());
        result.put("brand", model.getBrand());
        result.put("model_name", model.getModelName());
        return result;
    }

    /**
     * 无读取资格统一隐藏正文。
     */
    private TicketEntity readable(IdentityDTO actor, TicketEntity ticket) {
        if (ticket == null || !SupportAuthorization.canRead(actor, ticket))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return ticket;
    }

    /**
     * 正式处理动作仅当前有效技术处理人可执行。
     */
    private void handler(IdentityDTO actor, TicketEntity ticket) {
        SupportAuthorization.role(actor, "TECHNICIAN");
        if (!actor.getOwnerId().equals(ticket.getAssigneeId()))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    /**
     * 分类和记录允许当前范围客服或当前处理人。
     */
    private void handlerOrAgent(IdentityDTO actor, TicketEntity ticket) {
        if (actor.getRoles().contains("SERVICE_AGENT"))
            SupportAuthorization.agent(actor, ticket.getCustomerId());
        else handler(actor, ticket);
    }

    /**
     * 客户动作校验本人及原组织读取资格。
     */
    private void requester(IdentityDTO actor, TicketEntity ticket) {
        SupportAuthorization.role(actor, "CUSTOMER_USER");
        if (!actor.getOwnerId().equals(ticket.getRequesterId()))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    /**
     * 活跃人工处理状态。
     */
    private void active(TicketEntity ticket) {
        if (!Set.of(
                        TicketStatusEnum.SUBMITTED.getValue(),
                        TicketStatusEnum.PROCESSING.getValue(),
                        TicketStatusEnum.WAITING_CUSTOMER.getValue(),
                        TicketStatusEnum.WAITING_EXTERNAL.getValue())
                .contains(ticket.getStatus()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 精确状态前置条件。
     */
    private void state(TicketEntity ticket, String status) {
        if (!status.equals(ticket.getStatus()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 活动预约不得旁路修改，须先结束预约。
     */
    private void noBooking(TicketEntity ticket) {
        if (baseMapper.activeBookings(ticket.getId()) > 0)
            throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 原动作回执优先于新草稿期限；网络复核在工单事务外，提交时仍比较原版本。
     */
    private DraftValidation validateDraftAction(
            IdentityDTO actor, String id, TicketActionForm form, String draftId, String purpose) {
        TicketEntity ticket = readable(actor, baseMapper.selectById(id));
        if (draftId != null) {
            handler(actor, ticket);
            if (!ticket.getRowVersion().equals(form.getRowVersion()))
                throw new BusinessException(CommonErrorCode.CONFLICT);
        }
        return draftClient.validate(actor, draftId, purpose, ticket.getCustomerId(),
                ticket.getServiceItemId(), ticket.getDeviceId(), id, ticket.getRowVersion(), objectMapper.valueToTree(form));
    }

    /**
     * 本地原键和草稿根均先于远端校验；同根相同最终语义复用正式结果。
     */
    private ActionReceiptEntity replayAction(IdentityDTO actor, String key, String root, String hash) {
        ActionReceiptEntity value = receiptService.replay(actor, key, hash);
        return value == null ? receiptService.replayDraft(actor, root, hash) : value;
    }

    /**
     * 校验公开业务标识。
     */
    private void identifier(String value) {
        if (value == null || !value.matches("[0-9a-f]{32}"))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 后端生成32位业务标识。
     */
    private String id() {
        return IdentifierGenerator.next();
    }

    /**
     * 持锁期间只执行本地领域变更。 @author yangaobo
     */
    @FunctionalInterface
    private interface Change {
        /**
         * 在已授权且版本匹配的工单上执行动作。
         */
        void apply(TicketEntity ticket, LocalDateTime now, ObjectNode metadata);
    }
}
