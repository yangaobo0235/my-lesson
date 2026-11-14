package com.yangaobo.servicedesk.knowledge.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.knowledge.model.entity.IndexJobEntity;
import com.yangaobo.servicedesk.knowledge.model.form.IndexCreateForm;
import com.yangaobo.servicedesk.knowledge.model.form.KnowledgeQueryForm;
import com.yangaobo.servicedesk.knowledge.model.vo.IndexJobVO;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;

/**
 * 索引准备、业务提交、取消与原事实回查。
 *
 * @author yangaobo
 */
public interface IIndexJobService extends IService<IndexJobEntity> {
    /** 仅本地COMMITTED可作为202持久受理。 */
    IndexJobVO add(IdentityDTO actor, IndexCreateForm form);
    /** 原ID或原发起者请求键二选一查询。 */
    IndexJobVO query(IdentityDTO actor, KnowledgeQueryForm form);
    /** 原取消事实不回退，ABORTED后须最新文档版本显式重试。 */
    IndexJobVO cancel(IdentityDTO actor, IdVersionForm form);
    /** 受控重放已提交原指令，不重建任务身份或自动扫描投递。 */
    IndexJobVO replay(IdentityDTO actor,String jobId);
    /** Broker回查仅按原持久事实决策。 */
    TransactionResolution check(com.yangaobo.servicedesk.knowledge.model.dto.IndexCommand command);
    /** XXL有界收敛过期准备，不自动重发消息。 */
    int expire(int limit);
}
