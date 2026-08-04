package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.SeasonInsertDTO;
import com.yangaobo.dto.SeasonPageDTO;
import com.yangaobo.vo.SeasonSimpleListVO;
import com.yangaobo.dto.SeasonUpdateDTO;
import com.yangaobo.entity.Season;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 季次表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface SeasonService extends IService<Season> {
    boolean insert(SeasonInsertDTO dto);
    Season select(Long id);
    List<SeasonSimpleListVO> simpleList();
    PageVO<Season> page(SeasonPageDTO dto);
    boolean update(SeasonUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

}
