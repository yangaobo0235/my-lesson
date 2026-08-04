package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.SeckillDetailInsertDTO;
import com.yangaobo.dto.SeckillDetailPageDTO;
import com.yangaobo.dto.SeckillDetailUpdateDTO;
import com.yangaobo.entity.SeckillDetail;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 秒杀明细表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface SeckillDetailService extends IService<SeckillDetail> {
    boolean insert(SeckillDetailInsertDTO dto);
    SeckillDetail select(Long id);
    PageVO<SeckillDetail> page(SeckillDetailPageDTO dto);
    boolean update(SeckillDetailUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

}
