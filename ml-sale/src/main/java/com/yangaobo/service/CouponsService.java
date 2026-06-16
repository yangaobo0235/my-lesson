package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.CouponsInsertDTO;
import com.yangaobo.dto.CouponsPageDTO;
import com.yangaobo.dto.CouponsSimpleListVO;
import com.yangaobo.dto.CouponsUpdateDTO;
import com.yangaobo.entity.Coupons;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 优惠卷表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface CouponsService extends IService<Coupons> {
    boolean insert(CouponsInsertDTO dto);
    Coupons select(Long id);
    List<CouponsSimpleListVO> simpleList();
    PageVO<Coupons> page(CouponsPageDTO dto);
    boolean update(CouponsUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

    /**
     * 根据兑换口令查询优惠卷
     *
     * @param code 兑换口令
     * @return 优惠卷
     */
    Coupons selectByCode(String code);

}
