package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceEventEntity;
import com.yangaobo.servicedesk.ticket.model.form.support.ServiceEventsForm;
import com.yangaobo.servicedesk.ticket.model.vo.support.ServiceEventVO;

/**
 * 项目与资格变更审计的业务边界。
 *
 * @author yangaobo
 */
public interface IServiceEventService {
    /**
     * 在当前权限内读取同一操作者动作键，异义冲突。
     */
    ServiceEventEntity receipt(IdentityDTO actor, String key, String hash);

    /**
     * 与项目或资格变更同事务追加不可变事件。
     */
    void append(
            IdentityDTO actor,
            String service,
            String entitlement,
            String key,
            String hash,
            String type,
            Object before,
            Object after,
            String reason);

    /**
     * 按有权目标分页读取变更历史。
     */
    PageVO<ServiceEventVO> events(IdentityDTO actor, ServiceEventsForm form, boolean entitlement);
}
