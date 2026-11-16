package com.yangaobo.servicedesk.ticket.controller.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单时间线的对应控制器层；读取动作由工单入口承载。
 *
 * @author yangaobo
 */
@RestController
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketEventController {
}
