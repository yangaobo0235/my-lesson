package com.yangaobo.servicedesk.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentSeriesEntity;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Param;

/**
 * 资料系列及固定受众的持久化和行锁。
 *
 * @author yangaobo
 */
public interface DocumentSeriesMapper extends BaseMapper<DocumentSeriesEntity> {
    /** 固定行锁在业务短事务内使用，外部调用不得持锁。 */
    @Select("SELECT id, source_type, source_namespace, source_code, source_organization, language_code, audience, created_by, created_at FROM kb_document_series WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_DocumentSeriesEntity")
    DocumentSeriesEntity lock(@Param("id") String id);
    /** 取得全部行锁之后单独查询UTC决策时间。 */
    @Select("SELECT UTC_TIMESTAMP(6)")
    java.time.LocalDateTime decisionTime();
}
