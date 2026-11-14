package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.BusinessTransaction;
import com.yangaobo.servicedesk.ticket.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.ticket.error.DeviceErrorCode;
import com.yangaobo.servicedesk.ticket.mapper.DeviceMapper;
import com.yangaobo.servicedesk.ticket.mapper.ProductModelMapper;
import com.yangaobo.servicedesk.ticket.model.entity.DeviceEntity;
import com.yangaobo.servicedesk.ticket.model.entity.ProductModelEntity;
import com.yangaobo.servicedesk.ticket.model.form.device.DeviceCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.device.DeviceUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.device.DeviceVO;
import com.yangaobo.servicedesk.ticket.service.IDeviceService;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;
import com.yangaobo.servicedesk.ticket.service.TicketViews;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/**
 * 客户可选资产的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, DeviceEntity>
        implements IDeviceService {
    private final ProductModelMapper modelMapper;
    private final IdentityVerificationClient identityClient;
    private final BusinessTransaction transactions;

    /** {@inheritDoc} */
    @Override
    public PageVO<DeviceVO> listResources(IdentityDTO actor, SupportQueryForm form) {
        LambdaQueryWrapper<DeviceEntity> query = new LambdaQueryWrapper<>();
        if (actor.getRoles().contains("CUSTOMER_USER") || form.getCustomerId() != null)
            query.eq(
                    DeviceEntity::getCustomerId,
                    SupportAuthorization.customer(actor, form.getCustomerId()));
        else {
            if (!(actor.getRoles().contains("SERVICE_AGENT")
                    || actor.getRoles().contains("TECHNICIAN")))
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
            if (actor.getCustomerIds().isEmpty()) query.apply("1 = 0");
            else query.in(DeviceEntity::getCustomerId, actor.getCustomerIds());
        }
        query.eq(form.getStatus() != null, DeviceEntity::getStatus, form.getStatus());
        long total = baseMapper.selectCount(query);
        query.orderByAsc(DeviceEntity::getId)
                .last(
                        "LIMIT "
                                + form.getPageSize()
                                + " OFFSET "
                                + ((long) (form.getPageNum() - 1) * form.getPageSize()));
        PageVO<DeviceVO> result = new PageVO<>();
        result.setTotal(total);
        result.setPageNum(form.getPageNum());
        result.setPageSize(form.getPageSize());
        result.setRecords(baseMapper.selectList(query).stream().map(TicketViews::of).toList());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public DeviceVO query(IdentityDTO actor, String id) {
        DeviceEntity entity = baseMapper.selectById(id);
        if (entity == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        SupportAuthorization.customer(actor, entity.getCustomerId());
        return TicketViews.of(entity);
    }

    /** {@inheritDoc} */
    @Override
    public DeviceVO create(IdentityDTO actor, DeviceCreateForm form) {
        SupportAuthorization.agent(actor, form.getCustomerId());
        if (!identityClient.eligibility(actor, form.getCustomerId(), null).isCustomerActive())
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return transactions.execute(
                () -> {
                    ProductModelEntity model = modelMapper.lock(form.getProductModelId());
                    if (model == null || !"ACTIVE".equals(model.getStatus()))
                        throw new BusinessException(DeviceErrorCode.MODEL_UNAVAILABLE);
                    DeviceEntity entity = new DeviceEntity();
                    entity.setId(UUID.randomUUID().toString().replace("-", ""));
                    entity.setCustomerId(form.getCustomerId());
                    entity.setProductModelId(form.getProductModelId());
                    entity.setAssetCode(form.getAssetCode().strip().toUpperCase(Locale.ROOT));
                    entity.setSerialNumber(form.getSerialNumber());
                    entity.setLocation(form.getLocation());
                    entity.setServiceAreaCode(form.getServiceAreaCode());
                    entity.setStatus("ACTIVE");
                    entity.setCreatedBy(actor.getOwnerId());
                    entity.setRowVersion(0L);
                    entity.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
                    entity.setUpdatedAt(entity.getCreatedAt());
                    baseMapper.insert(entity);
                    return TicketViews.of(entity);
                },
                () -> null);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public DeviceVO updateResource(IdentityDTO actor, DeviceUpdateForm form) {
        DeviceEntity entity = locked(actor, form.getId(), form.getRowVersion());
        entity.setAssetCode(form.getAssetCode().strip().toUpperCase(Locale.ROOT));
        entity.setSerialNumber(form.getSerialNumber());
        entity.setLocation(form.getLocation());
        entity.setServiceAreaCode(form.getServiceAreaCode());
        return saveChange(entity);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public DeviceVO retire(IdentityDTO actor, IdVersionForm form) {
        DeviceEntity entity = locked(actor, form.getId(), form.getRowVersion());
        entity.setStatus("RETIRED");
        return saveChange(entity);
    }

    /** 设备客户和型号固定，写入口核对当前客服范围。 */
    private DeviceEntity locked(IdentityDTO actor, String id, long version) {
        DeviceEntity entity = baseMapper.lock(id);
        if (entity == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        SupportAuthorization.agent(actor, entity.getCustomerId());
        if (entity.getRowVersion() != version)
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return entity;
    }

    /** 清空语义显式写入，并且不回写既有工单或预约地址。 */
    private DeviceVO saveChange(DeviceEntity entity) {
        long version = entity.getRowVersion();
        entity.setRowVersion(version + 1);
        entity.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (baseMapper.update(
                        entity,
                        new LambdaUpdateWrapper<DeviceEntity>()
                                .eq(DeviceEntity::getId, entity.getId())
                                .eq(DeviceEntity::getRowVersion, version)
                                .set(
                                        entity.getSerialNumber() == null,
                                        DeviceEntity::getSerialNumber,
                                        null)
                                .set(entity.getLocation() == null, DeviceEntity::getLocation, null)
                                .set(
                                        entity.getServiceAreaCode() == null,
                                        DeviceEntity::getServiceAreaCode,
                                        null))
                != 1) throw new BusinessException(CommonErrorCode.CONFLICT);
        return TicketViews.of(entity);
    }
}
