package com.yangaobo.servicedesk.ticket.service;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.model.entity.ActionReceiptEntity;
import com.yangaobo.servicedesk.ticket.model.entity.AttachmentEntity;
import com.yangaobo.servicedesk.ticket.model.entity.OnsiteBookingEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;

import java.time.LocalDateTime;

/**
 * 本地正式结果的幂等权威；查询不依赖AI或重新执行原动作。
 *
 * @author yangaobo
 */
public interface IActionReceiptService {
    /**
     * 当前有权范围内核对原动作摘要；没有回执返回null，不推断不存在在途事务。
     */
    ActionReceiptEntity replay(IdentityDTO actor, String key, String hash);

    /**
     * 原草稿根先于新的AI校验对账；同根不同语义拒绝，原结果不重新执行。
     */
    ActionReceiptEntity replayDraft(IdentityDTO actor, String root, String hash);

    /**
     * 原操作者查询动作回执，不向其他有权工单人员开放其动作键。
     */
    TicketActionReceiptVO byAction(IdentityDTO actor, String key);

    /**
     * 查询本人草稿根已经提交的正式结果。
     */
    TicketActionReceiptVO byDraft(IdentityDTO actor, String root);

    /**
     * 内部维护仅核对根的原操作者及正式结果；无回执不推断在途动作失败。
     */
    TicketActionReceiptVO reconcileDraft(String ownerId, String root);

    /**
     * 在已持有的工单事务内插入一次回执并绑定新事件；拒绝覆盖既有结果。
     */
    ActionReceiptEntity record(IdentityDTO actor, TicketEntity ticket, OnsiteBookingEntity booking,
                               TicketEventEntity event, String type, String key, String hash,
                               String draftId, String draftRootId, LocalDateTime now);

    /**
     * 附件动作记录原附件状态和版本，与事件及业务变更同事务。
     */
    ActionReceiptEntity recordAttachment(IdentityDTO actor, TicketEntity ticket, AttachmentEntity attachment,
                                         TicketEventEntity event, String type, String key, String hash, LocalDateTime now);

    /**
     * 将已授权的原结果快照转为公开回执，不读取当前结果冒充历史。
     */
    TicketActionReceiptVO view(ActionReceiptEntity receipt);
}
