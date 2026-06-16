package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.NoticeInsertDTO;
import com.yangaobo.dto.NoticePageDTO;
import com.yangaobo.dto.NoticeUpdateDTO;
import com.yangaobo.entity.Notice;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 通知表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface NoticeService extends IService<Notice> {
    boolean insert(NoticeInsertDTO dto);
    Notice select(Long id);
    PageVO<Notice> page(NoticePageDTO dto);
    boolean update(NoticeUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

    /**
     * 查看前N条通知记录，根据序号升序，序号相同根据ID降序
     *
     * @param n 前N条
     * @return 前N条通知记录
     */
    List<Notice> top(Long n);

}
