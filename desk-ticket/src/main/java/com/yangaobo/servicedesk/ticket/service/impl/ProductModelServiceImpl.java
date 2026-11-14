package com.yangaobo.servicedesk.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.mapper.ProductModelMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ProductModelEntity;
import com.yangaobo.servicedesk.ticket.model.form.device.ProductModelCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.device.ProductModelUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.device.ProductModelVO;
import com.yangaobo.servicedesk.ticket.service.IProductModelService;
import com.yangaobo.servicedesk.ticket.service.SupportAuthorization;
import com.yangaobo.servicedesk.ticket.service.TicketViews;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 产品型号的业务边界。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ProductModelServiceImpl extends ServiceImpl<ProductModelMapper, ProductModelEntity>
        implements IProductModelService {
    /** {@inheritDoc} */
    @Override
    public PageVO<ProductModelVO> listResources(IdentityDTO actor, SupportQueryForm form) {
        requireReader(actor);
        LambdaQueryWrapper<ProductModelEntity> query =
                new LambdaQueryWrapper<ProductModelEntity>()
                        .eq(
                                form.getCategory() != null,
                                ProductModelEntity::getCategory,
                                form.getCategory())
                        .eq(
                                form.getStatus() != null,
                                ProductModelEntity::getStatus,
                                form.getStatus());
        long total = baseMapper.selectCount(query);
        query.orderByAsc(ProductModelEntity::getId)
                .last(
                        "LIMIT "
                                + form.getPageSize()
                                + " OFFSET "
                                + ((long) (form.getPageNum() - 1) * form.getPageSize()));
        PageVO<ProductModelVO> result = new PageVO<>();
        result.setTotal(total);
        result.setPageNum(form.getPageNum());
        result.setPageSize(form.getPageSize());
        result.setRecords(baseMapper.selectList(query).stream().map(TicketViews::of).toList());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public ProductModelVO query(IdentityDTO actor, String id) {
        requireReader(actor);
        ProductModelEntity entity = baseMapper.selectById(id);
        if (entity == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return TicketViews.of(entity);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProductModelVO create(IdentityDTO actor, ProductModelCreateForm form) {
        SupportAuthorization.role(actor, "SERVICE_AGENT");
        ProductModelEntity entity = new ProductModelEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setCategory(form.getCategory());
        entity.setBrand(form.getBrand().strip());
        entity.setModelName(form.getModelName().strip());
        entity.setSupportedSystems(form.getSupportedSystems());
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(actor.getOwnerId());
        entity.setRowVersion(0L);
        entity.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        entity.setUpdatedAt(entity.getCreatedAt());
        baseMapper.insert(entity);
        return TicketViews.of(entity);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProductModelVO updateResource(IdentityDTO actor, ProductModelUpdateForm form) {
        ProductModelEntity entity = locked(actor, form.getId(), form.getRowVersion());
        entity.setBrand(form.getBrand().strip());
        entity.setModelName(form.getModelName().strip());
        entity.setSupportedSystems(form.getSupportedSystems());
        return saveChange(entity);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProductModelVO disable(IdentityDTO actor, IdVersionForm form) {
        ProductModelEntity entity = locked(actor, form.getId(), form.getRowVersion());
        entity.setStatus("DISABLED");
        return saveChange(entity);
    }

    /** 型号更新以行锁和版本共同约束。 */
    private ProductModelEntity locked(IdentityDTO actor, String id, long version) {
        SupportAuthorization.role(actor, "SERVICE_AGENT");
        ProductModelEntity entity = baseMapper.lock(id);
        if (entity == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (entity.getRowVersion() != version)
            throw new BusinessException(CommonErrorCode.CONFLICT);
        return entity;
    }

    /** 显式清空可选说明，通用非空更新不能代替此语义。 */
    private ProductModelVO saveChange(ProductModelEntity entity) {
        long version = entity.getRowVersion();
        entity.setRowVersion(version + 1);
        entity.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (baseMapper.update(
                        entity,
                        new LambdaUpdateWrapper<ProductModelEntity>()
                                .eq(ProductModelEntity::getId, entity.getId())
                                .eq(ProductModelEntity::getRowVersion, version)
                                .set(
                                        entity.getSupportedSystems() == null,
                                        ProductModelEntity::getSupportedSystems,
                                        null))
                != 1) throw new BusinessException(CommonErrorCode.CONFLICT);
        return TicketViews.of(entity);
    }

    /** 目录阅读仅限实际支持角色，管理员不自动取得业务权限。 */
    private void requireReader(IdentityDTO actor) {
        if (!(actor.getRoles().contains("CUSTOMER_USER")
                || actor.getRoles().contains("SERVICE_AGENT")
                || actor.getRoles().contains("TECHNICIAN")
                || actor.getRoles().contains("KNOWLEDGE_ADMIN")))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
}
