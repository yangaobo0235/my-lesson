package com.yangaobo.servicedesk.knowledge.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentSeriesEntity;
import com.yangaobo.servicedesk.knowledge.model.form.SeriesCreateForm;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentSeriesVO;

/**
 * 固定资料来源、语言及受众的系列用例。
 *
 * @author yangaobo
 */
public interface IDocumentSeriesService extends IService<DocumentSeriesEntity> {
    /** 按固定身份读取系列，不依赖当前列表页。 */
    DocumentSeriesVO query(IdentityDTO actor,String id);
    /** 管理员分页选择系列。 */
    PageVO<DocumentSeriesVO> list(IdentityDTO actor,BasePageForm form);
    /** 来源身份创建后不随修订变化。 */
    DocumentSeriesVO add(IdentityDTO actor,SeriesCreateForm form);
}
