package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.ticket.model.entity.NotificationEntity;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.NotificationVO;

/**
 * 站内通知的业务边界。
 *
 * @author yangaobo
 */
public interface INotificationService extends IService<NotificationEntity> {
    /**
     * 当前有权通知分页，撤权内容不能继续展示。
     */
    PageVO<NotificationVO> list(IdentityDTO actor, BasePageForm form);

    /**
     * 首次已读幂等写入，重复读取不覆盖原时间。
     */
    void read(IdentityDTO actor, String id);
}
