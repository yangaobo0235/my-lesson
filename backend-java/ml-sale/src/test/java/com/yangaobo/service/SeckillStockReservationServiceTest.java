package com.yangaobo.service;

import com.yangaobo.component.MyRedis;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeckillStockReservationServiceTest {

    @Test
    void shouldMapAtomicReservationAndReleaseIdempotently() {
        MyRedis redis = mock(MyRedis.class);
        when(redis.lua(anyString(), anyList(), any(Object[].class)))
                .thenReturn(1L);
        SeckillStockReservationService service =
                new SeckillStockReservationService(redis);
        UUID requestId = UUID.randomUUID();

        var result = service.reserve(3L, 7L, 41L, requestId);
        boolean released = service.release(3L, 7L, 41L, requestId);

        assertThat(result).isEqualTo(
                SeckillStockReservationService.ReservationResult.RESERVED);
        assertThat(released).isTrue();
        verify(redis, org.mockito.Mockito.times(2)).lua(
                anyString(),
                org.mockito.ArgumentMatchers.eq(List.of(
                        "seckill:course_count:7",
                        "seckill:qualification:3:7:41",
                        "seckill:request:" + requestId,
                        "seckill:reconcile:pending",
                        "seckill:course_reserved_count:7")),
                any(Object[].class));
    }

    @Test
    void shouldLoadDueReservationForStableRequestReplay() {
        MyRedis redis = mock(MyRedis.class);
        UUID requestId = UUID.randomUUID();
        when(redis.zRangeByScore("seckill:reconcile:pending", 0, 5000))
                .thenReturn(java.util.Set.of(requestId.toString()));
        when(redis.get("seckill:request:" + requestId))
                .thenReturn("3|7|41|100.0|50.0|1000");
        SeckillStockReservationService service =
                new SeckillStockReservationService(redis);

        var reservations = service.pendingBefore(5000, 10);

        assertThat(reservations).containsExactly(
                new SeckillStockReservationService.Reservation(
                        requestId, 3L, 7L, 41L, 100.0, 50.0, 1000));
    }

    @Test
    void shouldRepairStockFromInitialAndReservedCounters() {
        MyRedis redis = mock(MyRedis.class);
        when(redis.lua(anyString(), anyList(), any(Object[].class))).thenReturn(1L);
        SeckillStockReservationService service =
                new SeckillStockReservationService(redis);

        assertThat(service.reconcileStock(7L)).isTrue();

        verify(redis).lua(
                anyString(),
                org.mockito.ArgumentMatchers.eq(List.of(
                        "seckill:course_initial_count:7",
                        "seckill:course_reserved_count:7",
                        "seckill:course_count:7")));
    }

    @Test
    void redisFailureIsNotRetriedWithAnotherRequestIdentity() {
        MyRedis redis = mock(MyRedis.class);
        when(redis.lua(anyString(), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));
        SeckillStockReservationService service =
                new SeckillStockReservationService(redis);

        assertThrows(
                IllegalStateException.class,
                () -> service.reserve(3L, 7L, 41L, UUID.randomUUID()));
        verify(redis).lua(anyString(), anyList(), any(Object[].class));
    }
}
