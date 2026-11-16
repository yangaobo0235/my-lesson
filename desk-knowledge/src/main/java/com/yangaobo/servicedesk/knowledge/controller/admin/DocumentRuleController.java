package com.yangaobo.servicedesk.knowledge.controller.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资料适用规则的对应控制器层；规则随资料完整动作维护。
 *
 * @author yangaobo
 */
@RestController
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentRuleController {
}
