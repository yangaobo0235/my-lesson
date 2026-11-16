package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 站内通知的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class NotificationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 通知标识。
     */
    @Schema(description = "通知标识。")
    private String id;

    /**
     * 当前有权工单。
     */
    @Schema(description = "当前有权工单。")
    private String ticketId;

    /**
     * 对应事件。
     */
    @Schema(description = "对应事件。")
    private String eventId;

    /**
     * 当前有权工单标题。
     */
    @Schema(description = "当前有权工单标题。")
    private String title;

    /**
     * 事件类型，不缓存正文。
     */
    @Schema(description = "事件类型，不缓存正文。")
    private String eventType;

    /**
     * UTC通知时间。
     */
    @Schema(description = "UTC通知时间。")
    private Instant createdAt;

    /**
     * 首次已读，未读为空。
     */
    @Schema(description = "首次已读，未读为空。")
    private Instant readAt;
}
