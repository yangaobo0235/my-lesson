package com.yangaobo.servicedesk.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEventEntity;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 不可变资料动作审计的持久化和行锁。
 *
 * @author yangaobo
 */
public interface DocumentEventMapper extends BaseMapper<DocumentEventEntity> {
    /**
     * 固定行锁在业务短事务内使用，外部调用不得持锁。
     */
    DocumentEventEntity lock(@Param("id") String id);

    /**
     * 取得全部行锁之后单独查询UTC决策时间。
     */
    LocalDateTime decisionTime();

    /**
     * 读取不可变资料动作历史，列表与统计复用相同条件。
     *
     * @param documentId 已授权资料标识
     * @param limit      已校验的页大小或扫描上限
     * @param offset     已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<DocumentEventEntity> listEvents(@Param("documentId") String documentId, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 读取不可变资料动作历史的总数。
     *
     * @param documentId 已授权资料标识
     * @return 符合相同范围的总数
     */
    long countEvents(@Param("documentId") String documentId);
}
