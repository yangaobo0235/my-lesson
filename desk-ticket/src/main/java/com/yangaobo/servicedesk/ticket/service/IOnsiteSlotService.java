package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteSlotEntity;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteSlotForm;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteSlotUpdateForm;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteSlotVO;

/**
 * 人工核对出勤、发布和关停时段。
 *
 * @author yangaobo
 */
public interface IOnsiteSlotService extends IService<OnsiteSlotEntity> {
    /**
     * 分页查看管理员可管理的时段。
     */
    PageVO<OnsiteSlotVO> list(IdentityDTO actor, BasePageForm form);

    /**
     * 建立尚未发布的固定窗口。
     */
    OnsiteSlotVO add(IdentityDTO actor, OnsiteSlotForm form);

    /**
     * 读取单个管理时段。
     */
    OnsiteSlotVO query(IdentityDTO actor, String id);

    /**
     * 比较原版本后修改草稿时段。
     */
    OnsiteSlotVO update(IdentityDTO actor, OnsiteSlotUpdateForm form);

    /**
     * 发布后冻结窗口和容量；关停保留现有预约。
     */
    OnsiteSlotVO change(IdentityDTO actor, IdVersionForm form, boolean close);
}
