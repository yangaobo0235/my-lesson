package com.yangaobo.component;

import com.yangaobo.dto.OrderMessage;
import com.yangaobo.mapper.OrderDetailMapper;
import com.yangaobo.mapper.OrderMapper;
import com.yangaobo.component.MyRedis;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.yangaobo.feign.UserFeign;

class OrderMessageListenerTest {

    @Test
    void shouldIgnoreDuplicateRequestBeforeCreatingAnotherOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderDetailMapper detailMapper = mock(OrderDetailMapper.class);
        MyRedis redis = mock(MyRedis.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(0);
        OrderMessageListener listener = new OrderMessageListener();
        ReflectionTestUtils.setField(listener, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(listener, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(listener, "orderDetailMapper", detailMapper);
        ReflectionTestUtils.setField(listener, "redis", redis);
        OrderMessage message = new OrderMessage();
        message.setRequestId(UUID.randomUUID());
        message.setFkSeckillId(3L);
        message.setFkUserId(41L);
        message.setFkCourseId(7L);

        listener.onMessage(message);

        verify(orderMapper, never()).insert(any());
        verify(detailMapper, never()).insert(any());
        verify(redis).setNxEx(
                org.mockito.ArgumentMatchers.eq("seckill:qualification:3:7:41"),
                org.mockito.ArgumentMatchers.eq(message.getRequestId().toString()),
                org.mockito.ArgumentMatchers.eq(24L),
                org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.HOURS));
        verify(redis).zRem("seckill:reconcile:pending", message.getRequestId().toString());
    }

    @Test
    void consumerFailurePropagatesSoBrokerCanRedeliver() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderDetailMapper detailMapper = mock(OrderDetailMapper.class);
        UserFeign userFeign = mock(UserFeign.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(userFeign.select(41L)).thenThrow(new IllegalStateException("user service down"));
        OrderMessageListener listener = new OrderMessageListener();
        ReflectionTestUtils.setField(listener, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(listener, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(listener, "orderDetailMapper", detailMapper);
        ReflectionTestUtils.setField(listener, "userFeign", userFeign);
        OrderMessage message = new OrderMessage();
        message.setRequestId(UUID.randomUUID());
        message.setFkSeckillId(3L);
        message.setFkUserId(41L);
        message.setFkCourseId(7L);
        message.setPrice(100.0);
        message.setSkPrice(50.0);

        assertThrows(IllegalStateException.class, () -> listener.onMessage(message));
        verify(orderMapper, never()).insert(any());
        verify(detailMapper, never()).insert(any());
    }
}
