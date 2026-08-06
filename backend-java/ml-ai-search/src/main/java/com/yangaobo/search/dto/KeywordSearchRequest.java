package com.yangaobo.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KeywordSearchRequest(
        @NotBlank @Size(max = 500) String query,
        @Min(1) @Max(100) int topK,
        @Size(max = 10) List<@NotBlank @Size(max = 32) String> sourceTypes) {
}
