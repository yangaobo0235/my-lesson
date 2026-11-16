package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceItemEntity;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceItemCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceItemUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceStatusForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceItemVO;

/**
 * IT服务项目的业务边界。
 *
 * @author yangaobo
 */
public interface IServiceItemService extends IService<ServiceItemEntity> {
    /**
     * 管理员分页查询全局服务目录。
     */
    PageVO<ServiceItemVO> listItems(IdentityDTO actor, SupportQueryForm form);

    /**
     * 管理员读取目标项目。
     */
    ServiceItemVO query(IdentityDTO actor, String id);

    /**
     * 按原动作键创建服务目录及事件。
     */
    ServiceItemVO create(IdentityDTO actor, ServiceItemCreateForm form);

    /**
     * 带版本调整支持条件，既有工单快照不回写。
     */
    ServiceItemVO updateItem(IdentityDTO actor, ServiceItemUpdateForm form);

    /**
     * 停用阻止新开通和新明确项目受理。
     */
    ServiceItemVO disable(IdentityDTO actor, ServiceStatusForm form);
}
