package com.yangaobo.servicedesk.knowledge.task;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.yangaobo.servicedesk.knowledge.service.IIndexJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * XXL有界触发准备过期收敛，不扫描自动发送消息。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeIndexPrepareTask {
    private final IIndexJobService indexJobService;

    /**
     * 重复触发按数据库PREPARING条件收敛。
     */
    @XxlJob("knowledgeIndexPrepareExpire")
    public void execute() {
        int count = indexJobService.expire(100);
        XxlJobHelper.handleSuccess("已收敛准备数：" + count);
    }
}
