package com.yangaobo.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yangaobo.component.MyRedis;
import com.yangaobo.ai.outbox.AiOutboxPublisher;
import com.yangaobo.constant.ML;
import com.yangaobo.dto.NoticeInsertDTO;
import com.yangaobo.dto.NoticePageDTO;
import com.yangaobo.dto.NoticeUpdateDTO;
import com.yangaobo.entity.Notice;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.mapper.NoticeMapper;
import com.yangaobo.result.ResultCode;
import com.yangaobo.service.NoticeService;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.yangaobo.entity.table.NoticeTableDef.NOTICE;

/**
 * 通知表 服务层实现。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {
    @Resource
    private MyRedis myRedis;
    @Resource
    private AiOutboxPublisher aiOutboxPublisher;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean insert(NoticeInsertDTO dto) {
        // 组装实体类
        Notice notice = BeanUtil.copyProperties(dto, Notice.class);
        notice.setCreated(LocalDateTime.now());
        notice.setUpdated(LocalDateTime.now());
        // insert into notice (content, idx, created, updated) values (?, ?, ?, ?)
        if (mapper.insert(notice) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库添加失败");
        }
        // 清除缓存
        myRedis.deleteByPrefix(ML.Redis.TOP_NOTICE_KEY_PREFIX);
        publish("NOTICE_UPDATED", notice.getId(), notice.getUpdated());
        return true;
    }

    @Override
    public Notice select(Long id) {
        // select * from notice where id = ?
        Notice notice = mapper.selectOneWithRelationsById(id);
        if (ObjectUtil.isNull(notice)) {
            throw new ServiceException(ResultCode.NOTICE_NOT_FOUND, id + "号通知数据不存在");
        }
        return notice;
    }

    @Override
    public PageVO<Notice> page(NoticePageDTO dto) {
        QueryChain<Notice> queryChain = QueryChain.of(mapper)
                .orderBy(NOTICE.IDX.asc(), NOTICE.ID.desc());

        // DB分页并转为VO
        Page<Notice> result = queryChain.withRelations().page(new Page<>(dto.getPageNum(), dto.getPageSize()));
        PageVO<Notice> pageVO = new PageVO<>();
        BeanUtil.copyProperties(result, pageVO);
        pageVO.setPageNum(result.getPageNumber());
        return pageVO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean update(NoticeUpdateDTO dto) {
        Long id = dto.getId();

        // 检查通知是否存在
        this.existsById(id);

        // 组装实体类
        Notice notice = BeanUtil.copyProperties(dto, Notice.class);
        notice.setUpdated(LocalDateTime.now());

        // update notice set content = ?, idx = ?, updated = ? where id = ?
        if (!UpdateChain.of(notice)
                .where(NOTICE.ID.eq(id))
                .update()) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库更新失败");
        }
        // 清除缓存
        myRedis.deleteByPrefix(ML.Redis.TOP_NOTICE_KEY_PREFIX);
        publish("NOTICE_UPDATED", id, notice.getUpdated());
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean delete(Long id) {
        // 检查通知是否存在
        this.existsById(id);

        // delete from notice where id = ?
        if (mapper.deleteById(id) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库删除失败");
        }
        // 清除缓存
        myRedis.deleteByPrefix(ML.Redis.TOP_NOTICE_KEY_PREFIX);
        publish("NOTICE_DELETED", id, LocalDateTime.now());
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deleteBatch(List<Long> ids) {
        // 检查通知是否存在
        // select count(*) from notice where id in (?, ?, ?)
        if (QueryChain.of(mapper)
                .where(NOTICE.ID.in(ids))
                .count() < ids.size()) {
            throw new ServiceException(ResultCode.NOTICE_NOT_FOUND, "至少一个通知数据不存在");
        }

        // delete from notice where id in (?)
        if (mapper.deleteBatchByIds(ids) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库删除失败");
        }
        // 清除缓存
        myRedis.deleteByPrefix(ML.Redis.TOP_NOTICE_KEY_PREFIX);
        LocalDateTime deletedAt = LocalDateTime.now();
        ids.forEach(id -> publish("NOTICE_DELETED", id, deletedAt));
        return true;
    }

    /**
     * 按主键检查通知是否存在，如果不存在则直接抛出异常
     *
     * @param id 通知主键
     */
    private void existsById(Long id) {
        // select count(*) from notice where id = ?
        if (!QueryChain.of(mapper)
                .where(NOTICE.ID.eq(id))
                .exists()) {
            throw new ServiceException(ResultCode.NOTICE_NOT_FOUND, id + "号通知数据不存在");
        }
    }

    @Override
    public List<Notice> top(Long n) {
        String redisKey = ML.Redis.TOP_NOTICE_KEY_PREFIX + n;

        // 若缓存命中，则直接返回
        if (myRedis.exists(redisKey)) {
            return JSONUtil.toList(myRedis.get(redisKey), Notice.class);
        }

        // select * from notice order by idx asc, id desc limit ?
        List<Notice> result = QueryChain.of(mapper)
                .orderBy(NOTICE.IDX.asc(), NOTICE.ID.desc())
                .limit(n)
                .list();

        // 将查询结果存入 redis 缓存
        myRedis.setEx(redisKey, JSONUtil.toJsonStr(result), 3, TimeUnit.HOURS);

        // 返回查询结果
        return result;
    }

    private void publish(
            String eventType,
            Long id,
            LocalDateTime updated) {
        aiOutboxPublisher.publish(
                eventType,
                id,
                updated.toInstant(
                        ZoneOffset.ofHours(8)).toEpochMilli());
    }

}
