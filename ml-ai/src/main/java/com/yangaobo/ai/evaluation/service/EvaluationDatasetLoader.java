package com.yangaobo.ai.evaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.evaluation.model.EvaluationCase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class EvaluationDatasetLoader {

    public static final String DATASET_VERSION = "m15-240-v3";
    private final ObjectMapper objectMapper;

    public EvaluationDatasetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<EvaluationCase> load() {
        ClassPathResource resource = new ClassPathResource(
                "evaluation/m15-evaluation-v3.jsonl");
        List<EvaluationCase> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode json = objectMapper.readTree(line);
                result.add(new EvaluationCase(
                        json.path("id").asText(),
                        json.path("type").asText(),
                        json.path("question").asText(),
                        json));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load evaluation dataset", exception);
        }
        if (result.size() != 240) {
            throw new IllegalStateException(
                    "Evaluation dataset must contain exactly 240 cases");
        }
        return List.copyOf(result);
    }
}
