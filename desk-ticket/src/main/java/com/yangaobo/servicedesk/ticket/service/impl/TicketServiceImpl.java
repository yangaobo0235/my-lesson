package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.yangaobo.servicedesk.ticket.model.entity.DeviceEntity;
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
import com.yangaobo.servicedesk.ticket.service.TicketViews;

import java.time.LocalDateTime;
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
        implements ITicketService, ITicketDeadlineService {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketVO create(IdentityDTO actor, TicketCreateForm form) {
        SupportAuthorization.role(actor, "CUSTOMER_USER");
        form.setTitle(form.getTitle().strip());
        form.setSymptom(form.getSymptom().strip());
        String hash = digest.hash("CREATE", actor.getOrganizationId(), form);
        TicketEntity old = creationReceipt(actor, form.getClientRequestId(), hash);
        if (old != null) return TicketViews.of(old);
        JsonNode handover =
                draftClient.validate(
                        actor,
                        form.getSourceDraftId(),
                        "CREATE_TICKET",
                        actor.getOrganizationId(),
                        form.getServiceItemId(),
                        form.getDeviceId(),
                        null,
                        null);
        JsonNode context = context(form.getSupportContext(), "USER_REPORT");
        return transactions.execute(
                () -> {
                    TicketEntity replay = creationReceipt(actor, form.getClientRequestId(), hash);
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
                    TicketEntity ticket = new TicketEntity();
                    ticket.setId(id());
                    ticket.setTicketNo("SD" + ticket.getId());
                    ticket.setCustomerId(actor.getOrganizationId());
                    ticket.setRequesterId(actor.getOwnerId());
                    ticket.setClientRequestId(form.getClientRequestId());
                    ticket.setRequestHash(hash);
                    ticket.setSourceDraftId(form.getSourceDraftId());
                    ticket.setHandoverSnapshot(handover);
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
                    ticket.setCreatedAt(now);
                    ticket.setUpdatedAt(now);
                    ticket.setAcceptanceDueAt(now.plusHours(4));
                    baseMapper.insert(ticket);
                    append(
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
                    return TicketViews.of(ticket);
                },
                () -> {
                    TicketEntity replay = creationReceipt(actor, form.getClientRequestId(), hash);
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
        TicketEventEntity event =
                eventMapper.selectOne(
                        new LambdaQueryWrapper<TicketEventEntity>()
                                .eq(TicketEventEntity::getTicketId, ticketId)
                                .eq(TicketEventEntity::getActorId, actor.getOwnerId())
                                .eq(TicketEventEntity::getClientActionId, key));
        if (event == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return receipt(event);
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
                    ticket.setSupportContext(context(form.getSupportContext(), "HUMAN_RECORD"));
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
                (ticket, now, metadata) -> {
                    handlerOrAgent(actor, ticket);
                    active(ticket);
                    noBooking(ticket);
                    if (form.getOnsiteEligible()
                            && (ticket.getServiceItemId() == null
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
                (ticket, now, metadata) -> {
                    SupportAuthorization.role(actor, "TECHNICIAN");
                    state(ticket, TicketStatusEnum.SUBMITTED.getValue());
                    recordOverdue(ticket, now);
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
        String hash = digest.hash(type, id, form);
        TicketEntity initial = readable(actor, baseMapper.selectById(id));
        SupportAuthorization.agent(actor, initial.getCustomerId());
        TicketEventEntity replay = replay(actor, initial, form.getClientActionId(), hash);
        if (replay != null) return receipt(replay);
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
                (ticket, now, metadata) -> {
                    SupportAuthorization.agent(actor, ticket.getCustomerId());
                    if (reassign) {
                        if (!Set.of(
                                        TicketStatusEnum.PROCESSING.getValue(),
                                        TicketStatusEnum.WAITING_CUSTOMER.getValue())
                                .contains(ticket.getStatus())
                                || form.getAssigneeId().equals(ticket.getAssigneeId()))
                            throw new BusinessException(CommonErrorCode.CONFLICT);
                    } else state(ticket, TicketStatusEnum.SUBMITTED.getValue());
                    metadata.put("previous_assignee_id", ticket.getAssigneeId());
                    metadata.put("assignee_id", form.getAssigneeId());
                    if (ticket.getFirstAcceptedAt() == null) {
                        recordOverdue(ticket, now);
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
            IdentityDTO actor, String id, TicketContentForm form) {
        return mutate(
                actor,
                id,
                form,
                "NEED_INFO",
                form,
                VisibilityEnum.PUBLIC.getValue(),
                form.getContent(),
                (ticket, now, metadata) -> {
                    handler(actor, ticket);
                    state(ticket, TicketStatusEnum.PROCESSING.getValue());
                    ticket.setStatus(TicketStatusEnum.WAITING_CUSTOMER.getValue());
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
                (ticket, now, metadata) -> {
                    requester(actor, ticket);
                    state(ticket, TicketStatusEnum.WAITING_CUSTOMER.getValue());
                    ticket.setStatus(TicketStatusEnum.PROCESSING.getValue());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO comment(IdentityDTO actor, String id, TicketCommentForm form) {
        TicketActionReceiptVO original =
                validateDraftAction(
                        actor, id, form, "COMMENTED", form.getSourceDraftId(), "ADD_RECORD");
        if (original != null) return original;
        return mutate(
                actor,
                id,
                form,
                "COMMENTED",
                form,
                form.getVisibility(),
                form.getContent(),
                (ticket, now, metadata) -> {
                    handlerOrAgent(actor, ticket);
                    active(ticket);
                    if (form.getSourceDraftId() != null)
                        metadata.put("source_draft_id", form.getSourceDraftId());
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketActionReceiptVO resolve(IdentityDTO actor, String id, TicketResolveForm form) {
        TicketActionReceiptVO original =
                validateDraftAction(
                        actor, id, form, "RESOLVED", form.getSourceDraftId(), "RESOLVE_TICKET");
        if (original != null) return original;
        return mutate(
                actor,
                id,
                form,
                TicketStatusEnum.RESOLVED.getValue(),
                form,
                VisibilityEnum.PUBLIC.getValue(),
                form.getContent(),
                (ticket, now, metadata) -> {
                    handler(actor, ticket);
                    state(ticket, TicketStatusEnum.PROCESSING.getValue());
                    noBooking(ticket);
                    if (form.getSourceDraftId() != null)
                        metadata.put("source_draft_id", form.getSourceDraftId());
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
                (ticket, now, metadata) -> {
                    active(ticket);
                    noBooking(ticket);
                    if (!actor.getOwnerId().equals(ticket.getRequesterId()))
                        handlerOrAgent(actor, ticket);
                    ticket.setStatus(TicketStatusEnum.CANCELLED.getValue());
                    ticket.setCancelledAt(now);
                    ticket.setOnsiteEligible(false);
                });
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
            Change change) {
        identifier(id);
        String hash = digest.hash(type, id, input);
        return transactions.execute(
                () -> {
                    TicketEntity ticket = readable(actor, baseMapper.lock(id));
                    TicketEventEntity replay =
                            replay(actor, ticket, form.getClientActionId(), hash);
                    if (replay != null) return receipt(replay);
                    if (!ticket.getRowVersion().equals(form.getRowVersion()))
                        throw new BusinessException(CommonErrorCode.CONFLICT);
                    String from = ticket.getStatus();
                    LocalDateTime now = baseMapper.decisionTime();
                    ObjectNode metadata = objectMapper.createObjectNode();
                    change.apply(ticket, now, metadata);
                    save(ticket, now);
                    return receipt(
                            append(
                                    ticket,
                                    actor,
                                    type,
                                    form.getClientActionId(),
                                    hash,
                                    from,
                                    visibility,
                                    content,
                                    metadata,
                                    now));
                },
                () -> {
                    TicketEntity ticket = readable(actor, baseMapper.selectById(id));
                    TicketEventEntity event = replay(actor, ticket, form.getClientActionId(), hash);
                    return event == null ? null : receipt(event);
                });
    }

    /**
     * {@inheritDoc} 每笔重核后写入，不将扫描候选当当前状态。
     */
    @Override
    public int scan(int limit) {
        if (limit < 1 || limit > 200) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        LocalDateTime time = baseMapper.decisionTime();
        List<TicketEntity> candidates = baseMapper.scanDue(time, limit);
        int count = 0;
        for (TicketEntity candidate : candidates) {
            boolean changed =
                    transactions.execute(
                            () -> {
                                TicketEntity ticket = baseMapper.lock(candidate.getId());
                                LocalDateTime now = baseMapper.decisionTime();
                                if (TicketStatusEnum.SUBMITTED.getValue().equals(ticket.getStatus())
                                        && ticket.getAcceptanceOverdueRecordedAt() == null
                                        && now.isAfter(ticket.getAcceptanceDueAt())) {
                                    recordOverdue(ticket, now);
                                    save(ticket, now);
                                    return true;
                                }
                                if (TicketStatusEnum.RESOLVED.getValue().equals(ticket.getStatus())
                                        && !now.isBefore(ticket.getConfirmationDueAt())) {
                                    ticket.setStatus(TicketStatusEnum.CLOSED.getValue());
                                    ticket.setClosureReason(
                                            ClosureReasonEnum.CONFIRMATION_TIMEOUT.getValue());
                                    ticket.setClosedAt(now);
                                    save(ticket, now);
                                    append(
                                            ticket,
                                            null,
                                            TicketStatusEnum.CLOSED.getValue(),
                                            null,
                                            null,
                                            TicketStatusEnum.RESOLVED.getValue(),
                                            VisibilityEnum.PUBLIC.getValue(),
                                            "处理结果超过72小时未反馈，按期限结单",
                                            objectMapper
                                                    .createObjectNode()
                                                    .put(
                                                            "closure_reason",
                                                            ClosureReasonEnum.CONFIRMATION_TIMEOUT
                                                                    .getValue()),
                                            now);
                                    return true;
                                }
                                return false;
                            },
                            () -> false);
            if (changed) count++;
        }
        return count;
    }

    /**
     * 持有工单锁的条件版本写入，空值通过实体策略清空。
     */
    private void save(TicketEntity ticket, LocalDateTime now) {
        long version = ticket.getRowVersion();
        ticket.setRowVersion(version + 1);
        ticket.setUpdatedAt(now);
        if (baseMapper.update(
                ticket,
                new LambdaUpdateWrapper<TicketEntity>()
                        .eq(TicketEntity::getId, ticket.getId())
                        .eq(TicketEntity::getRowVersion, version))
                != 1) throw new BusinessException(CommonErrorCode.CONFLICT);
    }

    /**
     * 补记首次受理超期，内部事件不发送给客户。
     */
    private void recordOverdue(TicketEntity ticket, LocalDateTime now) {
        if (ticket.getAcceptanceOverdueRecordedAt() == null
                && now.isAfter(ticket.getAcceptanceDueAt())) {
            ticket.setAcceptanceOverdueRecordedAt(now);
            append(
                    ticket,
                    null,
                    "ACCEPTANCE_OVERDUE",
                    null,
                    null,
                    ticket.getStatus(),
                    VisibilityEnum.INTERNAL.getValue(),
                    "已超过首次受理期限",
                    objectMapper.createObjectNode(),
                    now);
        }
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
     * 原键回执仅在当前读取权限内返回，同键异义拒绝。
     */
    private TicketEventEntity replay(
            IdentityDTO actor, TicketEntity ticket, String key, String hash) {
        TicketEventEntity event =
                eventMapper.selectOne(
                        new LambdaQueryWrapper<TicketEventEntity>()
                                .eq(TicketEventEntity::getTicketId, ticket.getId())
                                .eq(TicketEventEntity::getActorId, actor.getOwnerId())
                                .eq(TicketEventEntity::getClientActionId, key));
        if (event != null && !event.getActionRequestHash().equals(hash))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return event;
    }

    /**
     * 原建单键在当前本人及原组织权限内读取。
     */
    private TicketEntity creationReceipt(IdentityDTO actor, String key, String hash) {
        TicketEntity ticket =
                baseMapper.selectOne(
                        new LambdaQueryWrapper<TicketEntity>()
                                .eq(TicketEntity::getRequesterId, actor.getOwnerId())
                                .eq(TicketEntity::getClientRequestId, key));
        if (ticket == null) return null;
        readable(actor, ticket);
        if (!hash.equals(ticket.getRequestHash()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return ticket;
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
                        TicketStatusEnum.WAITING_CUSTOMER.getValue())
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
    private TicketActionReceiptVO validateDraftAction(
            IdentityDTO actor,
            String id,
            TicketActionForm form,
            String type,
            String draftId,
            String purpose) {
        if (draftId == null) return null;
        TicketEntity ticket = readable(actor, baseMapper.selectById(id));
        TicketEventEntity old =
                replay(actor, ticket, form.getClientActionId(), digest.hash(type, id, form));
        if (old != null) return receipt(old);
        handler(actor, ticket);
        if (!ticket.getRowVersion().equals(form.getRowVersion()))
            throw new BusinessException(CommonErrorCode.CONFLICT);
        draftClient.validate(
                actor,
                draftId,
                purpose,
                ticket.getCustomerId(),
                ticket.getServiceItemId(),
                ticket.getDeviceId(),
                id,
                ticket.getRowVersion());
        return null;
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
     * 返回原动作提交时的结果。
     */
    private TicketActionReceiptVO receipt(TicketEventEntity event) {
        TicketActionReceiptVO result = new TicketActionReceiptVO();
        result.setEventId(event.getId());
        result.setTicketId(event.getTicketId());
        result.setSequenceNumber(event.getSequenceNumber());
        result.setToStatus(event.getToStatus());
        return result;
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
