package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.OrderDetailExcelDTO;
import com.yangaobo.dto.OrderDetailInsertDTO;
import com.yangaobo.dto.OrderDetailPageDTO;
import com.yangaobo.dto.OrderDetailUpdateDTO;
import com.yangaobo.entity.OrderDetail;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 订单明细表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface OrderDetailService extends IService<OrderDetail> {
    boolean insert(OrderDetailInsertDTO dto);
    OrderDetail select(Long id);
    PageVO<OrderDetail> page(OrderDetailPageDTO dto);
    boolean update(OrderDetailUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);
    /**
     * 获取订单明细记录的Excel数据
     *
     * @return 订单明细的Excel数据列表
     */
    List<OrderDetailExcelDTO> getExcelData();


}
