package com.yangaobo.servicedesk.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 不可变原件修订与发布事实的持久化和行锁。
 *
 * @author yangaobo
 */
public interface DocumentMapper extends BaseMapper<DocumentEntity> {
    /**
     * 固定行锁在业务短事务内使用，外部调用不得持锁。
     */
    DocumentEntity lock(@Param("id") String id);

    /**
     * 取得全部行锁之后单独查询UTC决策时间。
     */
    LocalDateTime decisionTime();

    /**
     * 读取管理员修订目录，列表与统计复用相同条件。
     *
     * @param seriesId 可选系列筛选
     * @param limit    已校验的页大小或扫描上限
     * @param offset   已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<DocumentEntity> listDocuments(@Param("seriesId") String seriesId, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 读取管理员修订目录的总数。
     *
     * @param seriesId 可选系列筛选
     * @return 符合相同范围的总数
     */
    long countDocuments(@Param("seriesId") String seriesId);

    /**
     * 读取当前已授权的修订，列表与统计复用相同条件。
     *
     * @param ids    当前已授权修订集合；空集合不返回数据
     * @param limit  已校验的页大小或扫描上限
     * @param offset 已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<DocumentEntity> listAuthorized(@Param("ids") List<String> ids, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 读取当前已授权的修订的总数。
     *
     * @param ids 当前已授权修订集合；空集合不返回数据
     * @return 符合相同范围的总数
     */
    long countAuthorized(@Param("ids") List<String> ids);
}
