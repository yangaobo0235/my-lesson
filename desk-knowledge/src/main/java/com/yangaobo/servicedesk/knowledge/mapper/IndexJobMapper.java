package com.yangaobo.servicedesk.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.IndexJobEntity;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 索引尝试及SUBMIT和CANCEL事务事实的持久化和行锁。
 *
 * @author yangaobo
 */
public interface IndexJobMapper extends BaseMapper<IndexJobEntity> {
    /**
     * 固定行锁在业务短事务内使用，外部调用不得持锁。
     */
    IndexJobEntity lock(@Param("id") String id);

    /**
     * 取得全部行锁之后单独查询UTC决策时间。
     */
    LocalDateTime decisionTime();

    /**
     * 有界扫描准备超时候选；终止前仍按文档和任务锁序核验
     *
     * @param now   数据库UTC决策时刻
     * @param limit 已校验的页大小或扫描上限
     * @return 按稳定顺序返回的有界结果
     */
    List<IndexJobEntity> scanPreparingDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
