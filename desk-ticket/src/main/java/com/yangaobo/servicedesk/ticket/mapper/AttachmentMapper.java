package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.AttachmentEntity;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 附件意图查询、固定锁序与显式空值更新。
 *
 * @author yangaobo
 */
public interface AttachmentMapper extends BaseMapper<AttachmentEntity> {
    /**
     * 操作者范围的原上传意图。
     */
    AttachmentEntity byRequest(@Param("actor") String actor, @Param("key") String key);

    /**
     * 必须先锁工单再锁附件。
     */
    AttachmentEntity lock(@Param("id") String id);

    /**
     * 授权后数据库分页，不向客户返回内部附件。
     */
    List<AttachmentEntity> listReadable(@Param("ticket") String ticket, @Param("customer") boolean customer,
                                        @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 与列表使用相同筛选。
     */
    long countReadable(@Param("ticket") String ticket, @Param("customer") boolean customer);

    /**
     * 不读取正文或存储凭据的AI可读附件游标查询。
     */
    List<AttachmentEntity> listRedacted(@Param("ticket") String ticket, @Param("customer") boolean customer,
                                        @Param("after") String after);

    /**
     * 持锁更新可变投影，保留原上传事实并显式写入 NULL。
     */
    int saveState(@Param("value") AttachmentEntity value, @Param("expected") long expected);
}
