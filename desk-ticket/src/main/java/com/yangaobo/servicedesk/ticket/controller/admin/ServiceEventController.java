package com.yangaobo.servicedesk.ticket.controller.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目和资格变更事件的对应控制器层；读取动作由项目及资格入口承载。
 *
 * @author yangaobo
 */
@RestController
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceEventController {
}
