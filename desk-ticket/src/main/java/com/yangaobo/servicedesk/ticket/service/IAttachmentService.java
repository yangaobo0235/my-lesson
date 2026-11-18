package com.yangaobo.servicedesk.ticket.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.BasePageForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.ticket.model.entity.AttachmentEntity;
import com.yangaobo.servicedesk.ticket.model.dto.attachment.RedactedAttachment;
import com.yangaobo.servicedesk.ticket.model.form.attachment.AttachmentRedactionForm;
import com.yangaobo.servicedesk.ticket.model.form.attachment.AttachmentUploadForm;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketActionForm;
import com.yangaobo.servicedesk.ticket.model.vo.attachment.AttachmentVO;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;

/**
 * 附件生命周期；原件与 AI 脱敏授权分别校验。
 *
 * @author yangaobo
 */
public interface IAttachmentService extends IService<AttachmentEntity> {
    /**
     * 意图先提交，再外写一次，原键重试只对账。
     */
    AttachmentVO upload(IdentityDTO actor, String ticketId, AttachmentUploadForm form, String name, byte[] bytes);

    /**
     * 原上传者查询原意图，并对未知原对象做只读核验。
     */
    AttachmentVO byRequest(IdentityDTO actor, String clientUploadId);

    /**
     * 已授权工单的数据库分页。
     */
    PageVO<AttachmentVO> list(IdentityDTO actor, String ticketId, BasePageForm form);

    /**
     * 已授权且明确允许AI使用的元信息，按ID游标最多返回21条用于下一页判断。
     */
    java.util.List<AttachmentVO> listRedacted(IdentityDTO actor, String ticketId, String afterId);

    /**
     * 有权人工读取完整原件，返回前重新核验删除状态。
     */
    byte[] original(IdentityDTO actor, String ticketId, String attachmentId);

    /**
     * 当前原用户有权且明确授权AI时读取固定脱敏版本，拒绝任何版本回退。
     */
    RedactedAttachment readRedacted(IdentityDTO actor, String ticketId, String attachmentId, long redactionVersion);

    /**
     * 仅原上传者确认新脱敏版本，正式结果与回执同事务。
     */
    TicketActionReceiptVO confirmRedaction(IdentityDTO actor, String ticketId, String attachmentId, AttachmentRedactionForm form);

    /**
     * 原上传者或当前有权处理人员逻辑撤销，不物理删除原件。
     */
    TicketActionReceiptVO deleteAttachment(IdentityDTO actor, String ticketId, String attachmentId, TicketActionForm form);
}
