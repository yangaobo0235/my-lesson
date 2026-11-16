package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.api.ticket.dto.CatalogEntryDTO;
import com.yangaobo.servicedesk.api.ticket.dto.SupportContextDTO;
import com.yangaobo.servicedesk.api.ticket.form.CatalogQueryForm;
import com.yangaobo.servicedesk.api.ticket.form.SupportAccessForm;

import java.util.List;

/**
 * HTTP和RPC共同使用的支持范围及可信上下文核验。
 *
 * @author yangaobo
 */
public interface ISupportAccessService {
    /**
     * 重新核验原身份、当前或历史受理资格以及设备归属。
     */
    SupportContextDTO check(SupportAccessForm form);

    /**
     * 仅知识管理员核对有界服务项目或型号集合。
     */
    List<CatalogEntryDTO> catalog(CatalogQueryForm form, boolean models);
}
