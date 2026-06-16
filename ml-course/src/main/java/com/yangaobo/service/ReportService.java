package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.ReportInsertDTO;
import com.yangaobo.dto.ReportPageDTO;
import com.yangaobo.dto.ReportUpdateDTO;
import com.yangaobo.entity.Report;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 举报表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface ReportService extends IService<Report> {
    boolean insert(ReportInsertDTO dto);
    Report select(Long id);
    PageVO<Report> page(ReportPageDTO dto);
    boolean update(ReportUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

    /**
     * 根据用户主键删除举报
     *
     * @param userId 用户主键
     * @return true 删除成功，false 删除失败
     */
    boolean deleteByUserId(Long userId);

    /**
     * 根据用户主键列表删除举报
     *
     * @param userIds 用户主键列表
     * @return true 删除成功，false 删除失败
     */
    boolean deleteByUserIds(List<Long> userIds);

}
