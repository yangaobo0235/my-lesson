package com.yangaobo.servicedesk.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Param;

/**
 * 不可变原件修订与发布事实的持久化和行锁。
 *
 * @author yangaobo
 */
public interface DocumentMapper extends BaseMapper<DocumentEntity> {
    /** 固定行锁在业务短事务内使用，外部调用不得持锁。 */
    @Select("SELECT id, series_id, client_upload_id, upload_request_hash, revision_number, title, content_format, original_file_name, storage_bucket, storage_key, storage_version_id, file_size_bytes, file_sha256, source_url, source_review_note, revision_label, issued_on, applicability_note, publication_status, current_job_id, replaces_document_id, published_at, published_by, deleted_at, created_by, created_at, updated_at, row_version FROM kb_document WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_DocumentEntity")
    DocumentEntity lock(@Param("id") String id);
    /** 取得全部行锁之后单独查询UTC决策时间。 */
    @Select("SELECT UTC_TIMESTAMP(6)")
    java.time.LocalDateTime decisionTime();
}
