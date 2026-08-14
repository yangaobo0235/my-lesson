package com.yangaobo.job;

import com.yangaobo.dto.OrderMessage;
import com.yangaobo.service.SeckillStockReservationService;
import com.yangaobo.service.SeckillStockReservationService.Reservation;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillReconciliationJobTest {

    @Test
    void shouldReplayStableRequestAndRepairStockInvariant() {
        SeckillStockReservationService reservations =
                mock(SeckillStockReservationService.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        UUID requestId = UUID.randomUUID();
        when(reservations.pendingBefore(any(Long.class), eq(20)))
                .thenReturn(List.of(new Reservation(
                        requestId, 3L, 7L, 41L, 100.0, 50.0, 1000)));
        when(reservations.activeCourseIds()).thenReturn(Set.of("7"));
        when(reservations.reconcileStock(7L)).thenReturn(true);
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        when(rocketMQTemplate.syncSend(eq("ml-topic:ml-tag"), any(OrderMessage.class)))
                .thenReturn(sendResult);
        SeckillReconciliationJob job = new SeckillReconciliationJob(
                reservations, rocketMQTemplate, 0, 20);

        var result = job.reconcile();

        assertThat(result.pending()).isEqualTo(1);
        assertThat(result.republished()).isEqualTo(1);
        assertThat(result.stockRepaired()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        ArgumentCaptor<OrderMessage> messageCaptor =
                ArgumentCaptor.forClass(OrderMessage.class);
        verify(rocketMQTemplate).syncSend(
                eq("ml-topic:ml-tag"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getRequestId()).isEqualTo(requestId);
    }
}
