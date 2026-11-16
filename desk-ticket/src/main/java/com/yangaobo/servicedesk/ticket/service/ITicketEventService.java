package com.yangaobo.servicedesk.ticket.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEntity;
import com.yangaobo.servicedesk.ticket.model.entity.TicketEventEntity;

import java.time.LocalDateTime;

/**
 * 工单事件与收件人通知的事务内追加合同。
 *
 * @author yangaobo
 */
public interface ITicketEventService {
    /**
     * 在调用者已持有工单锁的事务内追加事件和通知，不自行提交事务。
     *
     * @param ticket     已锁定并更新状态的工单
     * @param actor      操作者；系统期限动作可为空
     * @param type       事件类型
     * @param key        原动作幂等键；无客户端动作时可为空
     * @param hash       动作语义摘要；无客户端动作时可为空
     * @param from       变更前状态
     * @param visibility 事件可见性
     * @param content    处理正文
     * @param metadata   脱敏事件快照，将补充版本
     * @param now        锁取得后的数据库UTC时刻
     * @param bookingId  关联预约标识；非预约事件可为空
     * @return 已追加的事件
     */
    TicketEventEntity append(
            TicketEntity ticket,
            IdentityDTO actor,
            String type,
            String key,
            String hash,
            String from,
            String visibility,
            String content,
            ObjectNode metadata,
            LocalDateTime now,
            String bookingId);
}
