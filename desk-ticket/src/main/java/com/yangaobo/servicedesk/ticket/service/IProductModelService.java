package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.ticket.model.entity.ProductModelEntity;
import com.yangaobo.servicedesk.ticket.model.form.device.ProductModelCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.device.ProductModelUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.device.ProductModelVO;

/**
 * 产品型号的业务边界。
 *
 * @author yangaobo
 */
public interface IProductModelService extends IService<ProductModelEntity> {
    /** 在当前角色和客户范围内分页查询。 */
    PageVO<ProductModelVO> listResources(IdentityDTO actor, SupportQueryForm form);

    /** 读取当前有权详情，历史退役记录保留。 */
    ProductModelVO query(IdentityDTO actor, String id);

    /** 核验新关联资格后登记。 */
    ProductModelVO create(IdentityDTO actor, ProductModelCreateForm form);

    /** 带版本维护允许修改字段。 */
    ProductModelVO updateResource(IdentityDTO actor, ProductModelUpdateForm form);

    /** 停止新增关联，不物理删除历史。 */
    ProductModelVO disable(IdentityDTO actor, IdVersionForm form);
}
