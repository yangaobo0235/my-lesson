package com.yangaobo.servicedesk.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.IndexJobEntity;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Param;

/**
 * 索引尝试及SUBMIT和CANCEL事务事实的持久化和行锁。
 *
 * @author yangaobo
 */
public interface IndexJobMapper extends BaseMapper<IndexJobEntity> {
    /** 固定行锁在业务短事务内使用，外部调用不得持锁。 */
    @Select("SELECT id, document_id, attempt_number, requested_by, client_request_id, request_payload, request_hash, submit_state, submit_deadline_at, submit_committed_at, cancel_operation_id, cancel_requested_by, cancel_state, cancel_deadline_at, cancel_committed_at, trace_context, last_error_code, created_at, updated_at FROM kb_index_job WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_IndexJobEntity")
    IndexJobEntity lock(@Param("id") String id);
    /** 取得全部行锁之后单独查询UTC决策时间。 */
    @Select("SELECT UTC_TIMESTAMP(6)")
    java.time.LocalDateTime decisionTime();
}
