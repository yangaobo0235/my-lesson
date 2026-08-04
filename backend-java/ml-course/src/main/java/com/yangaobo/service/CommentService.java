package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.CommentInsertDTO;
import com.yangaobo.dto.CommentPageDTO;
import com.yangaobo.dto.CommentSimpleListVO;
import com.yangaobo.dto.CommentUpdateDTO;
import com.yangaobo.entity.Comment;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 评论表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface CommentService extends IService<Comment> {
    boolean insert(CommentInsertDTO dto);
    Comment select(Long id);
    List<CommentSimpleListVO> simpleList();
    PageVO<Comment> page(CommentPageDTO dto);
    boolean update(CommentUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

    /**
     * 根据用户主键删除评论
     *
     * @param userId 用户主键
     * @return true 删除成功，false 删除失败
     */
    boolean deleteByUserId(Long userId);

    /**
     * 根据用户主键列表删除评论
     *
     * @param userIds 用户主键列表
     * @return true 删除成功，false 删除失败
     */
    boolean deleteByUserIds(List<Long> userIds);

}
