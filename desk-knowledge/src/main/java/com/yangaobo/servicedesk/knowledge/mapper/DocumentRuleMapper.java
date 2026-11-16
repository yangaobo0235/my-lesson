package com.yangaobo.servicedesk.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentRuleEntity;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 草稿适用规则的持久化和行锁。
 *
 * @author yangaobo
 */
public interface DocumentRuleMapper extends BaseMapper<DocumentRuleEntity> {
    /**
     * 固定行锁在业务短事务内使用，外部调用不得持锁。
     */
    DocumentRuleEntity lock(@Param("id") String id);

    /**
     * 取得全部行锁之后单独查询UTC决策时间。
     */
    LocalDateTime decisionTime();

    /**
     * 有界读取适用规则，超过业务预算由调用者拒绝
     *
     * @param serviceItemId 支持上下文已授权的服务项目
     * @param limit         已校验的页大小或扫描上限
     * @return 按稳定顺序返回的有界结果
     */
    List<DocumentRuleEntity> scanForService(@Param("serviceItemId") String serviceItemId, @Param("limit") int limit);
}
