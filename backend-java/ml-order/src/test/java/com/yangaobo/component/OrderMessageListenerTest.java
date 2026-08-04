package com.yangaobo.component;

import com.yangaobo.dto.OrderMessage;
import com.yangaobo.mapper.OrderDetailMapper;
import com.yangaobo.mapper.OrderMapper;
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

class OrderMessageListenerTest {

    @Test
    void shouldIgnoreDuplicateRequestBeforeCreatingAnotherOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderDetailMapper detailMapper = mock(OrderDetailMapper.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(0);
        OrderMessageListener listener = new OrderMessageListener();
        ReflectionTestUtils.setField(listener, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(listener, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(listener, "orderDetailMapper", detailMapper);
        OrderMessage message = new OrderMessage();
        message.setRequestId(UUID.randomUUID());
        message.setFkSeckillId(3L);
        message.setFkUserId(41L);
        message.setFkCourseId(7L);

        listener.onMessage(message);

        verify(orderMapper, never()).insert(any());
        verify(detailMapper, never()).insert(any());
    }
}
