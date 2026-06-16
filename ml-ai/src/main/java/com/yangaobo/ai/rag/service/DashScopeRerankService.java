package com.yangaobo.ai.rag.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.SearchHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class DashScopeRerankService implements RerankService {

    private final RagProperties properties;
    private final String apiKey;
    private final RestClient restClient;

    public DashScopeRerankService(
            RagProperties properties,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.properties = properties;
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(
                properties.getRerank().getConnectTimeoutMillis());
        requestFactory.setReadTimeout(
                properties.getRerank().getReadTimeoutMillis());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<SearchHit> rerank(
            String query,
            List<SearchHit> candidates,
            int topN) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("DashScope API key is missing");
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<String> documents = candidates.stream()
                .map(hit -> hit.title() + "\n" + hit.snippet())
                .toList();
        RerankRequest request = new RerankRequest(
                properties.getRerank().getModel(),
                new RerankInput(query, documents),
                new RerankParameters(
                        false,
                        Math.min(topN, candidates.size()),
                        properties.getRerank().getInstruct()));

        RerankResponse response = restClient.post()
                .uri(properties.getRerank().getEndpoint())
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RerankResponse.class);
        if (response == null
                || response.output() == null
                || response.output().results() == null) {
            throw new IllegalStateException("DashScope rerank returned no results");
        }

        List<SearchHit> reranked = new ArrayList<>();
        response.output().results().forEach(result -> {
                    if (result.index() >= 0
                            && result.index() < candidates.size()) {
                        reranked.add(candidates
                                .get(result.index())
                                .withScore(result.relevanceScore()));
                    }
                });
        if (reranked.isEmpty()) {
            throw new IllegalStateException("DashScope rerank returned invalid indexes");
        }
        return List.copyOf(reranked);
    }

    private record RerankRequest(
            String model,
            RerankInput input,
            RerankParameters parameters
    ) {
    }

    private record RerankInput(
            String query,
            List<String> documents
    ) {
    }

    private record RerankParameters(
            @JsonProperty("return_documents")
            boolean returnDocuments,
            @JsonProperty("top_n")
            int topN,
            String instruct
    ) {
    }

    private record RerankResponse(RerankOutput output) {
    }

    private record RerankOutput(List<RerankResult> results) {
    }

    private record RerankResult(
            int index,
            @JsonProperty("relevance_score")
            double relevanceScore
    ) {
    }
}
