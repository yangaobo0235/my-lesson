package com.yangaobo.servicedesk.knowledge.service.impl;

import com.yangaobo.servicedesk.knowledge.service.IDocumentEventService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 资料不可变事件的对应服务层；追加和读取由资料用例承载。
 *
 * @author yangaobo
 */
@Service
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentEventServiceImpl implements IDocumentEventService {
}
