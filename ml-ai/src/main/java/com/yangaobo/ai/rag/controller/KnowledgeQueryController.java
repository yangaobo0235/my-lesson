package com.yangaobo.ai.rag.controller;

import com.yangaobo.ai.rag.model.AiAnswer;
import com.yangaobo.ai.rag.model.AskKnowledgeRequest;
import com.yangaobo.ai.rag.model.SearchHit;
import com.yangaobo.ai.rag.service.HybridKnowledgeSearchService;
import com.yangaobo.ai.rag.service.KnowledgeAnswerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/ai/knowledge")
public class KnowledgeQueryController {

    private final HybridKnowledgeSearchService searchService;
    private final KnowledgeAnswerService answerService;

    public KnowledgeQueryController(
            HybridKnowledgeSearchService searchService,
            KnowledgeAnswerService answerService) {
        this.searchService = searchService;
        this.answerService = answerService;
    }

    @GetMapping("/search")
    public List<SearchHit> search(
            @RequestParam(name = "query")
            @NotBlank @Size(max = 500) String query) {
        return searchService.search(query).hits();
    }

    @PostMapping("/ask")
    public AiAnswer ask(
            @Valid @RequestBody AskKnowledgeRequest request) {
        return answerService.answer(request.question());
    }
}
