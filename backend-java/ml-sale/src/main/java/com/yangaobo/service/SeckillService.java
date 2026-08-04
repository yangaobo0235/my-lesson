package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.KillDTO;
import com.yangaobo.dto.SeckillInsertDTO;
import com.yangaobo.dto.SeckillPageDTO;
import com.yangaobo.vo.SeckillSimpleListVO;
import com.yangaobo.dto.SeckillUpdateDTO;
import com.yangaobo.entity.Seckill;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 秒杀表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface SeckillService extends IService<Seckill> {
    boolean insert(SeckillInsertDTO dto);
    Seckill select(Long id);
    List<SeckillSimpleListVO> simpleList();
    PageVO<Seckill> page(SeckillPageDTO dto);
    boolean update(SeckillUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

    /**
     * 查询距离当前时间最近的前N条秒杀活动记录，根据开始时间升序
     *
     * @param n 前N条
     * @return 前N条秒杀活动记录
     */
    List<Seckill> near(Long n);

    /**
     * 查询今日的秒杀活动数据，根据开始时间升序
     *
     * @return 今日的秒杀活动数据
     */
    List<Seckill> today();
     /**
     * 秒杀活动报名
     *
     * @param dto 秒杀活动报名DTO
     * @return 是否报名成功
     */
    boolean kill(KillDTO dto);

}
