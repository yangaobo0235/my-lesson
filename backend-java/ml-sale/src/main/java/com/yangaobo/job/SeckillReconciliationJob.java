package com.yangaobo.job;

import com.yangaobo.dto.OrderMessage;
import com.yangaobo.service.SeckillStockReservationService;
import com.yangaobo.service.SeckillStockReservationService.Reservation;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class SeckillReconciliationJob {

    private static final String ORDER_DESTINATION = "ml-topic:ml-tag";

    private final SeckillStockReservationService reservations;
    private final RocketMQTemplate rocketMQTemplate;
    private final long pendingAgeMillis;
    private final int batchSize;

    public SeckillReconciliationJob(
            SeckillStockReservationService reservations,
            RocketMQTemplate rocketMQTemplate,
            @Value("${ml.seckill.reconciliation.pending-age-ms:30000}") long pendingAgeMillis,
            @Value("${ml.seckill.reconciliation.batch-size:100}") int batchSize) {
        this.reservations = reservations;
        this.rocketMQTemplate = rocketMQTemplate;
        this.pendingAgeMillis = pendingAgeMillis;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ml.seckill.reconciliation.fixed-delay-ms:15000}")
    public void reconcileScheduled() {
        ReconciliationResult result = reconcile();
        if (result.republished() > 0 || result.stockRepaired() > 0 || result.failed() > 0) {
            log.info(
                    "秒杀对账完成, pending={}, republished={}, stockRepaired={}, failed={}",
                    result.pending(),
                    result.republished(),
                    result.stockRepaired(),
                    result.failed());
        }
    }

    @XxlJob("reconcileSeckillOrders")
    public void reconcileXxlJob() {
        ReconciliationResult result = reconcile();
        XxlJobHelper.handleSuccess(
                "秒杀对账完成: pending=" + result.pending()
                        + ", republished=" + result.republished()
                        + ", stockRepaired=" + result.stockRepaired()
                        + ", failed=" + result.failed());
    }

    ReconciliationResult reconcile() {
        int pending = 0;
        int republished = 0;
        int stockRepaired = 0;
        int failed = 0;
        long cutoff = System.currentTimeMillis() - pendingAgeMillis;
        for (Reservation reservation : reservations.pendingBefore(cutoff, batchSize)) {
            pending++;
            try {
                SendResult result = rocketMQTemplate.syncSend(
                        ORDER_DESTINATION,
                        toMessage(reservation));
                if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                    throw new IllegalStateException("RocketMQ did not acknowledge reconciliation send");
                }
                republished++;
            } catch (RuntimeException exception) {
                failed++;
                log.warn(
                        "秒杀待处理订单重投失败, requestId={}",
                        reservation.requestId(),
                        exception);
            }
        }
        for (String courseId : reservations.activeCourseIds()) {
            try {
                if (reservations.reconcileStock(Long.parseLong(courseId))) {
                    stockRepaired++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn("秒杀库存对账失败, courseId={}", courseId, exception);
            }
        }
        return new ReconciliationResult(pending, republished, stockRepaired, failed);
    }

    private static OrderMessage toMessage(Reservation reservation) {
        OrderMessage message = new OrderMessage();
        UUID requestId = reservation.requestId();
        message.setRequestId(requestId);
        message.setQualificationId(requestId);
        message.setFkSeckillId(reservation.seckillId());
        message.setFkCourseId(reservation.courseId());
        message.setFkUserId(reservation.userId());
        message.setPrice(reservation.price());
        message.setSkPrice(reservation.seckillPrice());
        return message;
    }

    record ReconciliationResult(
            int pending,
            int republished,
            int stockRepaired,
            int failed) {
    }
}
