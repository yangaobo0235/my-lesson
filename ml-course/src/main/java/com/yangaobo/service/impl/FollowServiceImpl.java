package com.yangaobo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.relation.RelationManager;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yangaobo.dto.FollowInsertDTO;
import com.yangaobo.dto.FollowPageDTO;
import com.yangaobo.dto.FollowUpdateDTO;
import com.yangaobo.entity.Follow;
import com.yangaobo.entity.User;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.feign.UserFeign;
import com.yangaobo.mapper.FollowMapper;
import com.yangaobo.result.Result;
import com.yangaobo.result.ResultCode;
import com.yangaobo.security.SecurityContext;
import com.yangaobo.service.FollowService;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.yangaobo.entity.table.FollowTableDef.FOLLOW;

/**
 * 收藏表 服务层实现。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow>  implements FollowService{
    @Resource
    private UserFeign userFeign;

    @Override
    public boolean insert(FollowInsertDTO dto) {
        Long fkUserId = SecurityContext.requireUserId();
        dto.setFkUserId(fkUserId);

        // 组装实体类
        Follow follow = BeanUtil.copyProperties(dto, Follow.class);
        Result<User> userResult = userFeign.select(fkUserId);
        if (ObjectUtil.isNull(userResult)) {
            throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "用户微服务远程调用失败，请联系管理员。");
        }
        User user = userResult.getData();
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, fkUserId + "号用户数据不存在");
        }
        follow.setNickname(user.getNickname());
        follow.setCreated(LocalDateTime.now());
        follow.setUpdated(LocalDateTime.now());

        // insert into follow (fk_episode_id, fk_user_id, nickname, created, updated) values (?, ?, ?, ?, ?)
        if (mapper.insert(follow) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库添加失败");
        }
        return true;
    }

    @Override
    public Follow select(Long id) {
        // 指定联查字段
        RelationManager.addQueryRelations("episode");

        // select * from follow where id = ?
        Follow follow = mapper.selectOneWithRelationsById(id);
        if (ObjectUtil.isNull(follow)) {
            throw new ServiceException(ResultCode.FOLLOW_NOT_FOUND, id + "号收藏数据不存在");
        }
        SecurityContext.requireOwner(follow.getFkUserId());
        return follow;
    }

    @Override
    public PageVO<Follow> page(FollowPageDTO dto) {
        // 指定联查字段
        RelationManager.addQueryRelations("episode");
        QueryChain<Follow> queryChain = QueryChain.of(mapper);

        // episodeId条件
        if (ObjectUtil.isNotNull(dto.getFkEpisodeId())) {
            queryChain.where(FOLLOW.FK_EPISODE_ID.eq(dto.getFkEpisodeId()));
        }

        // userId条件
        Long userId = SecurityContext.isAdmin() && ObjectUtil.isNotNull(dto.getFkUserId())
                ? dto.getFkUserId()
                : SecurityContext.requireUserId();
        dto.setFkUserId(userId);
        queryChain.where(FOLLOW.FK_USER_ID.eq(userId));

        // DB分页并转为VO
        Page<Follow> result = queryChain.withRelations().page(new Page<>(dto.getPageNum(), dto.getPageSize()));
        PageVO<Follow> pageVO = new PageVO<>();
        BeanUtil.copyProperties(result, pageVO);
        pageVO.setPageNum(result.getPageNumber());
        return pageVO;
    }

    @Override
    public boolean update(FollowUpdateDTO dto) {

        Long id = dto.getId();
        Follow existing = select(id);
        Long fkUserId = existing.getFkUserId();
        dto.setFkUserId(fkUserId);

        // 检查收藏是否存在
        select(id);

        // 组装实体类
        Follow follow = BeanUtil.copyProperties(dto, Follow.class);
        Result<User> userResult = userFeign.select(fkUserId);
        if (ObjectUtil.isNull(userResult)) {
            throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "用户微服务远程调用失败，请联系管理员。");
        }
        User user = userResult.getData();
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, fkUserId + "号用户数据不存在");
        }
        follow.setNickname(user.getNickname());
        follow.setUpdated(LocalDateTime.now());
        // update follow set fk_episode_id = ?, fk_user_id = ?, nickname = ?, content = ?, updated = ? where id = ?
        if (!UpdateChain.of(follow)
                .where(FOLLOW.ID.eq(follow.getId()))
                .update()) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库更新失败");
        }
        return true;
    }

    @Override
    public boolean delete(Long id) {
        select(id);

        // delete from follow where id = ?
        if (mapper.deleteById(id) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库删除失败");
        }
        return true;
    }

    @Override
    public boolean deleteBatch(List<Long> ids) {
        Long userId = SecurityContext.requireUserId();
        // 检查收藏是否存在
        // select count(*) from follow where id in (?, ?, ?)
        if (QueryChain.of(mapper)
                .where(FOLLOW.ID.in(ids))
                .and(SecurityContext.isAdmin() ? FOLLOW.ID.isNotNull() : FOLLOW.FK_USER_ID.eq(userId))
                .count() < ids.size()) {
            throw new ServiceException(ResultCode.FOLLOW_NOT_FOUND, "至少一个收藏数据不存在");
        }

        // delete from follow where id in (ids)
        if (mapper.deleteBatchByIds(ids) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库删除失败");
        }
        return true;
    }

    /**
     * 按主键检查收藏是否存在，如果不存在则直接抛出异常
     *
     * @param id 收藏主键
     */
    private void existsById(Long id) {
        // select count(*) from follow where id = ?
        if (!QueryChain.of(mapper)
                .where(FOLLOW.ID.eq(id))
                .exists()) {
            throw new ServiceException(ResultCode.FOLLOW_NOT_FOUND, id + "号收藏数据不存在");
        }
    }

}
