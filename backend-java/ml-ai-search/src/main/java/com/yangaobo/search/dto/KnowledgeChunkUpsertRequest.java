package com.yangaobo.search.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record KnowledgeChunkUpsertRequest(
        UUID eventId,
        @NotBlank @Size(max = 32) String sourceType,
        @NotBlank @Size(max = 100) String sourceId,
        @Min(1) long contentVersion,
        @NotEmpty @Size(max = 500) List<@Valid Chunk> chunks) {

    public record Chunk(
            @NotBlank String chunkId,
            @Min(0) int chunkIndex,
            @NotBlank @Size(max = 500) String title,
            @NotBlank @Size(max = 100000) String content,
            @NotBlank @Size(max = 1000) String sourceUrl,
            @NotBlank @Size(max = 64) String contentHash) {
    }
}
