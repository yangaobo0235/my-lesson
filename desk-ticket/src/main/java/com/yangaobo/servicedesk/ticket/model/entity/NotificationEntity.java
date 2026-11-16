package com.yangaobo.servicedesk.ticket.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 站内通知的持久化映射。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@TableName(value = "sd_notification", autoResultMap = true)
public class NotificationEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，由所属后端生成。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 本地 FK → sd_ticket。
     */
    private String ticketId;

    /**
     * 本地复合 FK(event_id,ticket_id) → sd_ticket_event(id,ticket_id)。
     */
    private String eventId;

    /**
     * 外部接收账号 ID。
     */
    private String recipientId;

    /**
     * 接收人首次已读时间。
     */
    private LocalDateTime readAt;

    /**
     * 记录创建时间。
     */
    private LocalDateTime createdAt;
}
