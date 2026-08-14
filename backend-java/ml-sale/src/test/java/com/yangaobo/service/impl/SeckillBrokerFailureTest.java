package com.yangaobo.service.impl;

import com.yangaobo.dto.OrderMessage;
import com.yangaobo.exception.ServiceException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillBrokerFailureTest {

    @Test
    void brokerTimeoutReturnsUnknownAndPreservesStableRequestId() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        when(rocketMQTemplate.syncSend(
                anyString(),
                org.mockito.ArgumentMatchers.any(OrderMessage.class)))
                .thenThrow(new IllegalStateException("broker timeout"));
        SeckillServiceImpl service = new SeckillServiceImpl();
        ReflectionTestUtils.setField(service, "rocketmqTemplate", rocketMQTemplate);
        OrderMessage message = new OrderMessage();
        UUID requestId = UUID.randomUUID();
        message.setRequestId(requestId);

        assertThrows(ServiceException.class, () -> service.publishOrder(message));
        verify(rocketMQTemplate).syncSend("ml-topic:ml-tag", message);
        org.junit.jupiter.api.Assertions.assertEquals(requestId, message.getRequestId());
    }
}
