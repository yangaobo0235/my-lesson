package com.yangaobo.servicedesk.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentSeriesEntity;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 资料系列及固定受众的持久化和行锁。
 *
 * @author yangaobo
 */
public interface DocumentSeriesMapper extends BaseMapper<DocumentSeriesEntity> {
    /**
     * 固定行锁在业务短事务内使用，外部调用不得持锁。
     */
    DocumentSeriesEntity lock(@Param("id") String id);

    /**
     * 取得全部行锁之后单独查询UTC决策时间。
     */
    LocalDateTime decisionTime();

    /**
     * 读取管理员系列目录，列表与统计复用相同条件。
     *
     * @param limit  已校验的页大小或扫描上限
     * @param offset 已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<DocumentSeriesEntity> listSeries(@Param("limit") int limit, @Param("offset") long offset);

    /**
     * 读取管理员系列目录的总数。
     *
     * @return 符合相同范围的总数
     */
    long countSeries();
}
