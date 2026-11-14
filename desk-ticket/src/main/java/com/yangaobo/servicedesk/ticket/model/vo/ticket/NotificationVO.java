package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 站内通知的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class NotificationVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 通知标识。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "通知标识。")
    private String id;

    /** 当前有权工单。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前有权工单。")
    private String ticketId;

    /** 对应事件。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "对应事件。")
    private String eventId;

    /** 当前有权工单标题。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前有权工单标题。")
    private String title;

    /** 事件类型，不缓存正文。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "事件类型，不缓存正文。")
    private String eventType;

    /** UTC通知时间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "UTC通知时间。")
    private java.time.Instant createdAt;

    /** 首次已读，未读为空。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "首次已读，未读为空。")
    private java.time.Instant readAt;
}
