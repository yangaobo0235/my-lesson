package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.ticket.model.dto.onsite.OnsiteBookingResult;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteBookingEntity;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteBookingForm;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteCancelForm;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteFinishForm;
import com.yangaobo.servicedesk.ticket.model.form.onsite.OnsiteAssignmentForm;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteBookingVO;
import com.yangaobo.servicedesk.ticket.model.vo.onsite.OnsiteSlotVO;

/**
 * 同一本地事务确认预约、容量、事件与通知。
 *
 * @author yangaobo
 */
public interface IOnsiteBookingService extends IService<OnsiteBookingEntity> {
    /**
     * 当前有权工单的区域时段。
     */
    PageVO<OnsiteSlotVO> slots(IdentityDTO actor, String ticketId, BasePageForm form);

    /**
     * 当前有权工单的预约历史。
     */
    PageVO<OnsiteBookingVO> history(IdentityDTO actor, String ticketId, BasePageForm form);

    /**
     * 原键查证当前预约状态。
     */
    OnsiteBookingVO byRequest(IdentityDTO actor, String ticketId, String key);

    /**
     * 服务人员按预约身份查询，并独立核对工单范围。
     */
    OnsiteBookingVO query(IdentityDTO actor, String bookingId);

    /**
     * 已有同键同义回执不重新扣容量，调用方据回执标识区分创建和重放。
     */
    OnsiteBookingResult book(IdentityDTO actor, String ticketId, OnsiteBookingForm form);

    /**
     * 到访前取消，原动作回执优先于版本判断，只回补一次容量。
     */
    TicketActionReceiptVO cancel(IdentityDTO actor, String ticketId, String bookingId, OnsiteCancelForm form);

    /**
     * 当前处理人登记实际结果、执行人和说明。
     */
    TicketActionReceiptVO finish(IdentityDTO actor, String ticketId, String bookingId, OnsiteFinishForm form);

    /**
     * 当前客服或处理人调整已核验现场负责人。
     */
    TicketActionReceiptVO assign(IdentityDTO actor, String ticketId, String bookingId, OnsiteAssignmentForm form);
}
