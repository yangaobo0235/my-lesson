package com.yangaobo.relay;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KnowledgeEventRelayTest {

    @Test
    void springCanConstructRelayWhenTestConstructorAlsoExists() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.register(KnowledgeEventRelay.class);
            context.refresh();

            context.getBean(KnowledgeEventRelay.class);
        }
    }

    @Test
    void forwardsPayloadAndServiceIdentityToPython() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://agent:24109/internal/v1/knowledge/events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "service-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"eventId\":\"1\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        new KnowledgeEventRelay(restTemplate, "http://agent:24109", "service-token")
                .onMessage("{\"eventId\":\"1\"}");

        server.verify();
    }

    @Test
    void propagatesPythonFailureSoRocketMqCanRetry() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://agent:24109/internal/v1/knowledge/events"))
                .andRespond(withServerError());
        KnowledgeEventRelay relay =
                new KnowledgeEventRelay(restTemplate, "http://agent:24109", "service-token");

        assertThrows(IllegalStateException.class, () -> relay.onMessage("{}"));
        server.verify();
    }
}
