package com.yangaobo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.relation.RelationManager;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yangaobo.dto.CommentInsertDTO;
import com.yangaobo.dto.CommentPageDTO;
import com.yangaobo.dto.CommentSimpleListVO;
import com.yangaobo.dto.CommentUpdateDTO;
import com.yangaobo.entity.Comment;
import com.yangaobo.entity.User;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.feign.UserFeign;
import com.yangaobo.mapper.CommentMapper;
import com.yangaobo.result.Result;
import com.yangaobo.result.ResultCode;
import com.yangaobo.security.SecurityContext;
import com.yangaobo.service.CommentService;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.yangaobo.entity.table.CommentTableDef.COMMENT;

/**
 * 评论表 服务层实现。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment>  implements CommentService{
    @Resource
    private UserFeign userFeign;

    @Override
    public boolean insert(CommentInsertDTO dto) {
        Long fkUserId = SecurityContext.requireUserId();
        dto.setFkUserId(fkUserId);

        // 组装实体类
        Comment comment = BeanUtil.copyProperties(dto, Comment.class);
        Result<User> userResult = userFeign.select(fkUserId);
        if (ObjectUtil.isNull(userResult)) {
            throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "用户微服务远程调用失败，请联系管理员。");
        }
        User user = userResult.getData();
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, fkUserId + "号用户数据不存在");
        }
        comment.setNickname(user.getNickname());
        comment.setAvatar(user.getAvatar());
        comment.setProvince(user.getProvince());
        comment.setCreated(LocalDateTime.now());
        comment.setUpdated(LocalDateTime.now());

        // insert into comment (fk_episode_id, fk_user_id, nickname, avatar, province, pid, content, created, updated) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
        if (mapper.insert(comment) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库添加失败");
        }
        return true;
    }

    @Override
    public Comment select(Long id) {
        // 指定联查字段
        RelationManager.addQueryRelations("episode");

        // select * from comment where id = ?
        Comment comment = mapper.selectOneWithRelationsById(id);
        if (ObjectUtil.isNull(comment)) {
            throw new ServiceException(ResultCode.COMMENT_NOT_FOUND, id + "号评论数据不存在");
        }
        return comment;
    }

    @Override
    public List<CommentSimpleListVO> simpleList() {
        // select * from comment
        return QueryChain.of(mapper)
                .listAs(CommentSimpleListVO.class);
    }

    @Override
    public PageVO<Comment> page(CommentPageDTO dto) {
        // 指定联查字段
        RelationManager.addQueryRelations("episode");
        QueryChain<Comment> queryChain = QueryChain.of(mapper);

        // pid条件
        Long pid = dto.getPid();
        if (ObjectUtil.isNotNull(pid)) {
            queryChain.where(COMMENT.PID.eq(pid));
        }

        // fkEpisodeId条件
        Long episodeId = dto.getFkEpisodeId();
        if (ObjectUtil.isNotNull(episodeId)) {
            queryChain.where(COMMENT.FK_EPISODE_ID.eq(episodeId));
        }

        // fkUserId条件
        Long userId = dto.getFkUserId();
        if (ObjectUtil.isNotNull(userId)) {
            queryChain.where(COMMENT.FK_USER_ID.eq(userId));
        }

        // DB分页并转为VO
        Page<Comment> result = queryChain.withRelations().page(new Page<>(dto.getPageNum(), dto.getPageSize()));
        PageVO<Comment> pageVO = new PageVO<>();
        BeanUtil.copyProperties(result, pageVO);
        pageVO.setPageNum(result.getPageNumber());
        return pageVO;
    }

    @Override
    public boolean update(CommentUpdateDTO dto) {
        Long id = dto.getId();
        Comment existing = select(id);
        SecurityContext.requireOwner(existing.getFkUserId());
        Long fkUserId = existing.getFkUserId();
        dto.setFkUserId(fkUserId);

        // 检查评论是否存在
        select(id);

        // 组装实体类
        Comment comment = BeanUtil.copyProperties(dto, Comment.class);
        Result<User> userResult = userFeign.select(fkUserId);
        if (ObjectUtil.isNull(userResult)) {
            throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "用户微服务远程调用失败，请联系管理员。");
        }
        User user = userResult.getData();
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, fkUserId + "号用户数据不存在");
        }
        comment.setNickname(user.getNickname());
        comment.setAvatar(user.getAvatar());
        comment.setProvince(user.getProvince());
        comment.setUpdated(LocalDateTime.now());

        // update comment set fk_episode_id = ?, fk_user_id = ?, pid = ?, content = ?, nickname = ?, avatar = ?, province = ?, updated = ? where id = ?
        if (!UpdateChain.of(comment)
                .where(COMMENT.ID.eq(comment.getId()))
                .update()) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库更新失败");
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean delete(Long id) {
        Comment existing = select(id);
        SecurityContext.requireOwner(existing.getFkUserId());

        // 删除全部子评论
        // delete from comment where pid = id
        UpdateChain.of(mapper)
                .where(COMMENT.PID.eq(id))
                .remove();

        // 删除父评论
        // delete from comment where id = ?
        if (mapper.deleteById(id) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库删除失败");
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deleteBatch(List<Long> ids) {
        Long userId = SecurityContext.requireUserId();

        // 检查评论是否存在
        // select count(*) from comment where id in (?, ?, ?)
        if (QueryChain.of(mapper)
                .where(COMMENT.ID.in(ids))
                .and(SecurityContext.isAdmin() ? COMMENT.ID.isNotNull() : COMMENT.FK_USER_ID.eq(userId))
                .count() < ids.size()) {
            throw new ServiceException(ResultCode.COMMENT_NOT_FOUND, "至少一个评论数据不存在");
        }

        // 删除全部子评论
        // delete from comment where pid in (ids)
        UpdateChain.of(mapper)
                .where(COMMENT.PID.in(ids))
                .remove();

        // 删除父评论
        // delete from comment where id in (ids)
        if (mapper.deleteBatchByIds(ids) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库删除失败");
        }
        return true;
    }

    /**
     * 按主键检查评论是否存在，如果不存在则直接抛出异常
     *
     * @param id 评论主键
     */
    private void existsById(Long id) {
        // select count(*) from comment where id = ?
        if (!QueryChain.of(mapper)
                .where(COMMENT.ID.eq(id))
                .exists()) {
            throw new ServiceException(ResultCode.COMMENT_NOT_FOUND, id + "号评论数据不存在");
        }
    }

    @Override
    public boolean deleteByUserId(Long userId) {
        SecurityContext.requireOwner(userId);

        // 查询该用户的所有评论ID列表
        // select id from comment where fk_user_id = userId
        List<Long> commentIds = QueryChain.of(mapper)
                .select(COMMENT.ID)
                .where(COMMENT.FK_USER_ID.eq(userId))
                .listAs(Long.class);

        // 无评论记录，直接返回 ture 即可
        if (CollUtil.isEmpty(commentIds)) {
            return true;
        }

        // 查询子评论ID列表
        // select id from comment where pid in (commentIds)
        List<Long> subCommentIds = QueryChain.of(mapper)
                .select(COMMENT.ID)
                .where(COMMENT.PID.in(commentIds))
                .listAs(Long.class);

        // 组合父评论和子评论的ID列表
        if (CollUtil.isNotEmpty(subCommentIds)) {
            commentIds.addAll(subCommentIds);
        }

        // 删除组合后的评论
        // delete from comment where id in (commentIds)
        if (mapper.deleteBatchByIds(commentIds) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库删除失败");
        }
        return true;
    }

    @Override
    public boolean deleteByUserIds(List<Long> userIds) {

        // 查询该用户的所有评论ID列表
        // select id from comment where fk_user_id in (userIds)
        List<Long> commentIds = QueryChain.of(mapper)
                .select(COMMENT.ID)
                .where(COMMENT.FK_USER_ID.in(userIds))
                .listAs(Long.class);

        // 无评论记录
        if (CollUtil.isEmpty(commentIds)) {
            return true;
        }

        // 查询子评论ID列表
        // select id from comment where pid in (commentIds)
        List<Long> subCommentIds = QueryChain.of(mapper)
                .select(COMMENT.ID)
                .where(COMMENT.PID.in(commentIds))
                .listAs(Long.class);

        // 组合父评论和子评论的ID列表
        if (CollUtil.isNotEmpty(subCommentIds)) {
            commentIds.addAll(subCommentIds);
        }

        // 删除组合后的评论
        // delete from comment where id in (commentIds)
        if (mapper.deleteBatchByIds(commentIds) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库删除失败");
        }
        return true;
    }

}
