package com.yangaobo.servicedesk.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.knowledge.mapper.DocumentSeriesMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentSeriesEntity;
import com.yangaobo.servicedesk.knowledge.model.form.SeriesCreateForm;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentSeriesVO;
import com.yangaobo.servicedesk.knowledge.service.IDocumentSeriesService;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeRules;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeViews;

/**
 * 一个系列固定受众和来源，修订号由上传事务锁系列分配。
 *
 * @author yangaobo
 */
@org.springframework.stereotype.Service
@lombok.RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentSeriesServiceImpl extends ServiceImpl<DocumentSeriesMapper,DocumentSeriesEntity> implements IDocumentSeriesService {
    /** {@inheritDoc} */
    @Override
    public DocumentSeriesVO query(IdentityDTO actor,String id) {
        KnowledgeRules.administrator(actor);
        var value=baseMapper.selectById(id);
        if(value==null)throw new com.yangaobo.servicedesk.common.exception.BusinessException(com.yangaobo.servicedesk.common.base.CommonErrorCode.NOT_FOUND);
        return KnowledgeViews.of(value);
    }
    /** {@inheritDoc} */
    @Override
    public PageVO<DocumentSeriesVO> list(IdentityDTO actor,BasePageForm form) {
        KnowledgeRules.administrator(actor);var query=new LambdaQueryWrapper<DocumentSeriesEntity>();var result=new PageVO<DocumentSeriesVO>();
        result.setTotal(baseMapper.selectCount(query));query.orderByDesc(DocumentSeriesEntity::getCreatedAt).orderByAsc(DocumentSeriesEntity::getId)
            .last("LIMIT "+form.getPageSize()+" OFFSET "+((long)(form.getPageNum()-1)*form.getPageSize()));
        result.setRecords(baseMapper.selectList(query).stream().map(KnowledgeViews::of).toList());result.setPageNum(form.getPageNum());result.setPageSize(form.getPageSize());return result;
    }
    /** {@inheritDoc} */
    @Override
    public DocumentSeriesVO add(IdentityDTO actor,SeriesCreateForm form) {
        KnowledgeRules.administrator(actor);var value=new DocumentSeriesEntity();
        value.setId(java.util.UUID.randomUUID().toString().replace("-",""));value.setSourceType(form.getSourceType());
        value.setSourceNamespace(KnowledgeRules.normalize(form.getSourceNamespace(),true));value.setSourceCode(form.getSourceCode().strip());
        value.setSourceOrganization(form.getSourceOrganization().strip());value.setLanguageCode(form.getLanguageCode().strip().toLowerCase(java.util.Locale.ROOT));
        value.setAudience(form.getAudience());value.setCreatedBy(actor.getOwnerId());value.setCreatedAt(baseMapper.decisionTime());baseMapper.insert(value);return KnowledgeViews.of(value);
    }
}
