package com.yangaobo.servicedesk.knowledge.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.common.util.IdentifierGenerator;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentSeriesMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentSeriesEntity;
import com.yangaobo.servicedesk.knowledge.model.form.SeriesCreateForm;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentSeriesVO;
import com.yangaobo.servicedesk.knowledge.service.IDocumentSeriesService;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeRules;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeViews;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 一个系列固定受众和来源，修订号由上传事务锁系列分配。
 *
 * @author yangaobo
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentSeriesServiceImpl
        extends ServiceImpl<DocumentSeriesMapper, DocumentSeriesEntity>
        implements IDocumentSeriesService {
    /**
     * {@inheritDoc}
     */
    @Override
    public DocumentSeriesVO query(IdentityDTO actor, String id) {
        KnowledgeRules.administrator(actor);
        var value = baseMapper.selectById(id);
        if (value == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return KnowledgeViews.of(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageVO<DocumentSeriesVO> list(IdentityDTO actor, BasePageForm form) {
        KnowledgeRules.administrator(actor);
        long total = baseMapper.countSeries();
        var records = baseMapper.listSeries(form.getPageSize(), form.offset()).stream().map(KnowledgeViews::of).toList();
        return PageVO.of(records, total, form);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DocumentSeriesVO add(IdentityDTO actor, SeriesCreateForm form) {
        KnowledgeRules.administrator(actor);
        var value = new DocumentSeriesEntity();
        value.setId(IdentifierGenerator.next());
        value.setSourceType(form.getSourceType());
        value.setSourceNamespace(KnowledgeRules.normalize(form.getSourceNamespace(), true));
        value.setSourceCode(form.getSourceCode().strip());
        value.setSourceOrganization(form.getSourceOrganization().strip());
        value.setLanguageCode(form.getLanguageCode().strip().toLowerCase(Locale.ROOT));
        value.setAudience(form.getAudience());
        value.setCreatedBy(actor.getOwnerId());
        value.setCreatedAt(baseMapper.decisionTime());
        baseMapper.insert(value);
        return KnowledgeViews.of(value);
    }
}
