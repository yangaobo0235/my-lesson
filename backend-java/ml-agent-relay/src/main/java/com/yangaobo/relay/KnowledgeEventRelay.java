package com.yangaobo.relay;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RocketMQMessageListener(
        topic = "${ai.knowledge-sync.topic:ml-ai-knowledge-events}",
        consumerGroup = "${ai.knowledge-sync.consumer-group:ml-agent-python-relay}")
public class KnowledgeEventRelay implements RocketMQListener<String> {

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String internalToken;

    @Autowired
    public KnowledgeEventRelay(
            @Value("${ai.agent.base-url:http://127.0.0.1:24109}")
            String agentBaseUrl,
            @Value("${ai.internal-token:}") String internalToken) {
        this(new RestTemplate(), agentBaseUrl, internalToken);
    }

    KnowledgeEventRelay(
            RestTemplate restTemplate,
            String agentBaseUrl,
            String internalToken) {
        this.restTemplate = restTemplate;
        this.endpoint = agentBaseUrl + "/internal/v1/knowledge/events";
        this.internalToken = internalToken;
    }

    @Override
    public void onMessage(String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);
        try {
            restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(message, headers),
                    String.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Python knowledge ingestion failed",
                    exception);
        }
    }
}
