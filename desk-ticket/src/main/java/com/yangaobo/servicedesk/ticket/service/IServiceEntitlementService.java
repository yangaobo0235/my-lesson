package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.ticket.model.dto.support.ServiceAdmission;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEntitlementEntity;
import com.yangaobo.servicedesk.ticket.model.form.support.EntitlementGrantForm;
import com.yangaobo.servicedesk.ticket.model.form.support.EntitlementUpdateForm;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceStatusForm;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;
import com.yangaobo.servicedesk.ticket.model.vo.support.EntitledServiceVO;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceEntitlementVO;

/**
 * 客户服务资格的业务边界。
 *
 * @author yangaobo
 */
public interface IServiceEntitlementService extends IService<ServiceEntitlementEntity> {
    /**
     * 当前客服客户范围内分页资格。
     */
    PageVO<ServiceEntitlementVO> listEntitlements(IdentityDTO actor, SupportQueryForm form);

    /**
     * 当前客服范围内读取资格详情。
     */
    ServiceEntitlementVO query(IdentityDTO actor, String id);

    /**
     * 首次开通，不把重复客户项目静默当续期。
     */
    ServiceEntitlementVO grant(IdentityDTO actor, EntitlementGrantForm form);

    /**
     * 修改连续日期，客户和项目固定。
     */
    ServiceEntitlementVO updateEntitlement(IdentityDTO actor, EntitlementUpdateForm form);

    /**
     * 暂停或恢复原ID，恢复不延长期限。
     */
    ServiceEntitlementVO setSuspended(IdentityDTO actor, ServiceStatusForm form, boolean suspended);

    /**
     * 同一建单事务内按项目、资格顺序锁定并构造受理依据。
     */
    ServiceAdmission admit(String customerId, String serviceItemId);

    /**
     * 查询当前有权客户的有效支持项目。
     */
    PageVO<EntitledServiceVO> available(IdentityDTO actor, SupportQueryForm form);
}
