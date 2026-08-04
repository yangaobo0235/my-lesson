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
                        "seckill:qualification:3:7:41")),
                any(Object[].class));
    }
}
