package com.yangaobo.servicedesk.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangaobo.servicedesk.ticket.model.entity.ActionReceiptEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 统一回执的唯一身份查询，不提供回执内容更新SQL。
 *
 * @author yangaobo
 */
public interface ActionReceiptMapper extends BaseMapper<ActionReceiptEntity> {
    /**
     * 按原操作者和动作键查询，不按工单缩小唯一键范围。
     */
    ActionReceiptEntity byAction(@Param("actor") String actor, @Param("key") String key);

    /**
     * 按全局草稿根查询，拥有者及当前工单权限由用例核验。
     */
    ActionReceiptEntity byDraft(@Param("root") String root);
}
