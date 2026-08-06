package com.yangaobo.search.controller;

import com.yangaobo.search.dto.KeywordSearchRequest;
import com.yangaobo.search.dto.KeywordSearchResponse;
import com.yangaobo.search.dto.KnowledgeChunkUpsertRequest;
import com.yangaobo.search.dto.KnowledgeIndexResponse;
import com.yangaobo.search.service.KnowledgeSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/ai/knowledge")
public class KnowledgeSearchController {

    private final KnowledgeSearchService service;

    public KnowledgeSearchController(KnowledgeSearchService service) {
        this.service = service;
    }

    @PostMapping("/search/keyword")
    public KeywordSearchResponse search(@Valid @RequestBody KeywordSearchRequest request) {
        return service.search(request);
    }

    @PutMapping("/chunks")
    public KnowledgeIndexResponse upsert(
            @Valid @RequestBody KnowledgeChunkUpsertRequest request) {
        return service.upsert(request);
    }

    @DeleteMapping("/sources/{sourceType}/{sourceId}")
    public KnowledgeIndexResponse delete(
            @PathVariable @NotBlank @Size(max = 32) String sourceType,
            @PathVariable @NotBlank @Size(max = 100) String sourceId,
            @RequestParam @Min(1) long contentVersion) {
        return service.delete(sourceType, sourceId, contentVersion);
    }
}
