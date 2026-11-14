package com.yangaobo.servicedesk.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEventEntity;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Param;

/**
 * 不可变资料动作审计的持久化和行锁。
 *
 * @author yangaobo
 */
public interface DocumentEventMapper extends BaseMapper<DocumentEventEntity> {
    /** 固定行锁在业务短事务内使用，外部调用不得持锁。 */
    @Select("SELECT id, document_id, actor_id, event_type, related_document_id, details, created_at FROM kb_document_event WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_DocumentEventEntity")
    DocumentEventEntity lock(@Param("id") String id);
    /** 取得全部行锁之后单独查询UTC决策时间。 */
    @Select("SELECT UTC_TIMESTAMP(6)")
    java.time.LocalDateTime decisionTime();
}
