package com.yangaobo.servicedesk.knowledge.service.impl;

import com.yangaobo.servicedesk.knowledge.service.IDocumentRuleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 资料适用规则的对应服务层；规则由资料用例在原事务内维护。
 *
 * @author yangaobo
 */
@Service
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentRuleServiceImpl implements IDocumentRuleService {
}
