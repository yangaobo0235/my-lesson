package com.yangaobo.servicedesk.user.service;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.user.model.form.account.AuditQueryForm;
import com.yangaobo.servicedesk.user.model.vo.account.AuditEventVO;

/**
 * 管理审计查询的业务边界。
 *
 * @author yangaobo
 */
public interface IAdminAuditService {
    /**
     * 管理员按单一目标查询安全审计。
     */
    PageVO<AuditEventVO> list(IdentityDTO actor, AuditQueryForm form);
}
