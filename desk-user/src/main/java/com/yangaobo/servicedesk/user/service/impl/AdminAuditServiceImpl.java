package com.yangaobo.servicedesk.user.service.impl;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.user.mapper.AdminEventMapper;
import com.yangaobo.servicedesk.user.model.entity.AdminEventEntity;
import com.yangaobo.servicedesk.user.model.form.account.AuditQueryForm;
import com.yangaobo.servicedesk.user.model.vo.account.AuditEventVO;
import com.yangaobo.servicedesk.user.service.IAdminAuditService;
import com.yangaobo.servicedesk.user.service.IIdentityAdministrationService;
import com.yangaobo.servicedesk.user.service.IdentityAuditViews;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 管理审计查询的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class AdminAuditServiceImpl implements IAdminAuditService {
    private final AdminEventMapper adminEventMapper;
    private final IIdentityAdministrationService administration;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<AuditEventVO> list(IdentityDTO actor, AuditQueryForm form) {
        administration.requireAdministrator(actor);
        if (form.getTargetUserId() != null && form.getTargetOrganizationId() != null)
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);

        var records =
                adminEventMapper
                        .list(
                                form.getTargetUserId(),
                                form.getTargetOrganizationId(),
                                form.getPageSize(),
                                form.offset())
                        .stream()
                        .map(this::view)
                        .toList();
        long pageTotal =
                adminEventMapper.count(form.getTargetUserId(), form.getTargetOrganizationId());

        return PageVO.of(records, pageTotal, form);
    }

    /**
     * 审计读取转换持久JSON，保持公开合同与数据表隔离。
     */
    private AuditEventVO view(AdminEventEntity row) {
        AuditEventVO result = new AuditEventVO();
        result.setId(row.getId());
        result.setActorType(row.getActorType());
        result.setActorUserId(row.getActorUserId());
        result.setTargetUserId(row.getTargetUserId());
        result.setTargetOrganizationId(row.getTargetOrganizationId());
        result.setEventType(row.getEventType());
        result.setCreatedAt(row.getCreatedAt());
        result.setDetails(IdentityAuditViews.details(row.getDetails()));
        return result;
    }
}
