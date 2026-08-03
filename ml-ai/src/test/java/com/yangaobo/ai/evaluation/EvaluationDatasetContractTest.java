package com.yangaobo.ai.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationDatasetContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldContainTheRequiredRegressionCases() throws Exception {
        List<JsonNode> cases = load();

        assertThat(cases).hasSize(240);
        assertThat(countByType(cases)).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        "RAG", 100L,
                        "TOOL", 60L,
                        "SECURITY", 40L,
                        "NO_ANSWER", 40L));

        Set<String> ids = new HashSet<>();
        for (JsonNode item : cases) {
            assertThat(item.path("id").asText()).isNotBlank();
            assertThat(item.path("question").asText()).isNotBlank();
            assertThat(ids.add(item.path("id").asText())).isTrue();
            switch (item.path("type").asText()) {
                case "RAG" -> {
                    assertThat(item.path("expectedSources").isArray()).isTrue();
                    assertThat(item.path("mustContain").isArray()).isTrue();
                }
                case "TOOL" ->
                        assertThat(item.path("expectedTools").isArray()).isTrue();
                case "SECURITY" ->
                        assertThat(item.has("expectedRefusal")).isTrue();
                case "NO_ANSWER" ->
                        assertThat(item.path("expectedNoAnswer").asBoolean()).isTrue();
                default -> throw new AssertionError("Unknown evaluation type");
            }
        }
        assertThat(cases.stream()
                .filter(item -> "SECURITY".equals(item.path("type").asText()))
                .filter(item -> item.path("expectedRefusal").asBoolean())
                .count()).isEqualTo(30L);
        assertThat(cases.stream()
                .filter(item -> "SECURITY".equals(item.path("type").asText()))
                .filter(item -> !item.path("expectedRefusal").asBoolean())
                .count()).isEqualTo(10L);
    }

    private List<JsonNode> load() throws Exception {
        var resource = getClass().getResourceAsStream(
                "/evaluation/m15-evaluation-v3.jsonl");
        assertThat(resource).isNotNull();
        List<JsonNode> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    result.add(objectMapper.readTree(line));
                }
            }
        }
        return result;
    }

    private Map<String, Long> countByType(List<JsonNode> cases) {
        return cases.stream().collect(Collectors.groupingBy(
                item -> item.path("type").asText(),
                Collectors.counting()));
    }
}
