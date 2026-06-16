package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.FollowInsertDTO;
import com.yangaobo.dto.FollowPageDTO;
import com.yangaobo.dto.FollowUpdateDTO;
import com.yangaobo.entity.Follow;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 收藏表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface FollowService extends IService<Follow> {
    boolean insert(FollowInsertDTO dto);
    Follow select(Long id);
    PageVO<Follow> page(FollowPageDTO dto);
    boolean update(FollowUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

}
