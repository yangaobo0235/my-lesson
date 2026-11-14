package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ProductModelEntity;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/**
 * 产品型号的业务边界。
 *
 * @author yangaobo
 */
public interface ProductModelMapper extends BaseMapper<ProductModelEntity> {
    /** 按稳定标识锁定当前事实，JSON使用实体明确映射。 */
    @Select(
            "SELECT id, category, brand, model_name, supported_systems, status, created_by,"
                + " created_at, updated_at, row_version FROM it_product_model WHERE id = #{id} FOR"
                + " UPDATE")
    @ResultMap("mybatis-plus_ProductModelEntity")
    ProductModelEntity lock(@Param("id") String id);
}
