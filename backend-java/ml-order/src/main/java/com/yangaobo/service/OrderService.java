package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.*;
import com.yangaobo.entity.Order;
import com.yangaobo.vo.PageVO;

import java.util.List;
import java.util.Map;

/**
 * 订单表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface OrderService extends IService<Order> {
    boolean insert(OrderInsertDTO dto);
    Order select(Long id);
    PageVO<Order> page(OrderPageDTO dto);
    boolean update(OrderUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);
    /**
     * 统计订单数据，包括日增订单数量，订单总数和支付方式比例等。
     *
     * @return 统计结果
     */
    Map<String, Object> statistics();
    /**
     * 创建预支付订单记录（未支付）
     *
     * @param dto 预支付DTO实体类
     * @return 返回订单的 SN
     */
    String prePay(PrePayDTO dto);

    /**
     * 根据订单编号更新订单状态
     *
     * @param sn      订单编号
     * @param status  订单状态
     * @param payType 支付方式
     * @return 影响条目数
     */
    boolean updateStatusBySn(String sn, Integer status, Integer payType);

    /**
     * 根据订单编号查询订单
     *
     * @param sn 订单编号
     * @return 订单实体，不存在返回 null
     */
    Order selectBySn(String sn);

    /**
     * 根据订单编号检查订单支付状态
     *
     * @param sn 订单编号
     * @return 订单存在且支付成功返回 true，否则返回 false
     */
    boolean checkStatusBySn(String sn);

}
