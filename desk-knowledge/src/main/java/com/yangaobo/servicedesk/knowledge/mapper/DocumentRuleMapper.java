package com.yangaobo.servicedesk.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentRuleEntity;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Param;

/**
 * 草稿适用规则的持久化和行锁。
 *
 * @author yangaobo
 */
public interface DocumentRuleMapper extends BaseMapper<DocumentRuleEntity> {
    /** 固定行锁在业务短事务内使用，外部调用不得持锁。 */
    @Select("SELECT id, document_id, rule_number, service_item_id, product_model_id, os_name, os_version, software_name, software_version, created_at FROM kb_document_applicability WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_DocumentRuleEntity")
    DocumentRuleEntity lock(@Param("id") String id);
    /** 取得全部行锁之后单独查询UTC决策时间。 */
    @Select("SELECT UTC_TIMESTAMP(6)")
    java.time.LocalDateTime decisionTime();
}
