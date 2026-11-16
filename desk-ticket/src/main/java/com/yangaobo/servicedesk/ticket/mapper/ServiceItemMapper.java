package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ServiceItemEntity;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportQueryForm;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * IT服务项目的业务边界。
 *
 * @author yangaobo
 */
public interface ServiceItemMapper extends BaseMapper<ServiceItemEntity> {
    /**
     * 按稳定标识锁定当前事实，JSON使用实体明确映射。
     */
    ServiceItemEntity lock(@Param("id") String id);

    /**
     * 在调用者验证读取资格后查询目录，列表与统计复用相同条件。
     *
     * @param form   类别与状态筛选
     * @param limit  已校验的页大小或扫描上限
     * @param offset 已校验的非负分页偏移
     * @return 按稳定顺序返回的有界结果
     */
    List<ServiceItemEntity> listItems(@Param("form") SupportQueryForm form, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 在调用者验证读取资格后查询目录的总数。
     *
     * @param form 类别与状态筛选
     * @return 符合相同范围的总数
     */
    long countItems(@Param("form") SupportQueryForm form);
}
