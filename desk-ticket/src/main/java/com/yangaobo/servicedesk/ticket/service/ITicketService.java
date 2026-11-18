package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.form.ticket.OnsiteEligibilityForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketAssignmentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketCommentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketContentForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketWaitForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketInformationForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketContextForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketCreateForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketPriorityForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketQueryForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketResolveForm;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketDetailVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketEventVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketVO;

/**
 * 工单的业务边界。
 *
 * @author yangaobo
 */
public interface ITicketService extends IService<TicketEntity> {
    /**
     * 直接提交或采用客户确认草稿，先查原键再核验当前资格。
     */
    TicketVO create(IdentityDTO actor, TicketCreateForm form);

    /**
     * 当前本人或服务范围内工单列表。
     */
    PageVO<TicketVO> list(IdentityDTO actor, TicketQueryForm form);

    /**
     * 读取授权工单及公开时间线；内部备注按服务角色过滤。
     */
    TicketDetailVO query(IdentityDTO actor, String id);

    /**
     * 原请求键查询，结果不存在返回NOT_FOUND。
     */
    TicketVO byRequest(IdentityDTO actor, String clientRequestId);

    /**
     * 分类、环境和现场信息版本化更新。
     */
    TicketVO updateContext(IdentityDTO actor, String id, TicketContextForm form);

    /**
     * 人工核对现场能力、区域和地点后开放或撤回资格。
     */
    TicketActionReceiptVO onsiteEligibility(
            IdentityDTO actor, String id, OnsiteEligibilityForm form);

    /**
     * 首次接单或分派，超期在同一事务补记。
     */
    TicketActionReceiptVO accept(IdentityDTO actor, String id, TicketActionForm form);

    /**
     * 客服分派技术人员。
     */
    TicketActionReceiptVO assign(
            IdentityDTO actor, String id, TicketAssignmentForm form, boolean reassign);

    /**
     * 优先级只在未首次受理时调整。
     */
    TicketActionReceiptVO priority(IdentityDTO actor, String id, TicketPriorityForm form);

    /**
     * 请求客户补充信息。
     */
    TicketActionReceiptVO requestInformation(IdentityDTO actor, String id, TicketInformationForm form);

    /**
     * 当前处理人登记外部等待，保留责任人且不得携带活动预约。
     */
    TicketActionReceiptVO waitExternal(IdentityDTO actor, String id, TicketWaitForm form);

    /**
     * 当前处理人说明结束等待，恢复处理中。
     */
    TicketActionReceiptVO endWait(IdentityDTO actor, String id, TicketContentForm form);

    /**
     * 当前处理人记录跟进并设置新期限，递增轮次以允许下一次提醒。
     */
    TicketActionReceiptVO followUp(IdentityDTO actor, String id, TicketWaitForm form);

    /**
     * 客户补充后恢复处理。
     */
    TicketActionReceiptVO supplement(IdentityDTO actor, String id, TicketContentForm form);

    /**
     * 公开或内部处理记录。
     */
    TicketActionReceiptVO comment(IdentityDTO actor, String id, TicketCommentForm form);

    /**
     * 提交人工确认的解决/转介结果。
     */
    TicketActionReceiptVO resolve(IdentityDTO actor, String id, TicketResolveForm form);

    /**
     * 客户确认关闭。
     */
    TicketActionReceiptVO confirm(IdentityDTO actor, String id, TicketActionForm form);

    /**
     * 客户反馈未解决，重建处理期限。
     */
    TicketActionReceiptVO unresolved(IdentityDTO actor, String id, TicketContentForm form);

    /**
     * 按权取消并保留历史。
     */
    TicketActionReceiptVO cancel(IdentityDTO actor, String id, TicketContentForm form);

    /**
     * 当前权限下分页读取时间线。
     */
    PageVO<TicketEventVO> events(IdentityDTO actor, String ticketId, BasePageForm form);

    /**
     * 按原动作键查询已提交回执。
     */
    TicketActionReceiptVO actionReceipt(IdentityDTO actor, String ticketId, String key);
}
