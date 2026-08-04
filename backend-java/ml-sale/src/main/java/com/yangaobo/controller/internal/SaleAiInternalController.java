package com.yangaobo.controller.internal;

import com.yangaobo.dto.ai.AiCursorPage;
import com.yangaobo.dto.ai.ArticleKnowledgeDTO;
import com.yangaobo.dto.ai.NoticeKnowledgeDTO;
import com.yangaobo.dto.ai.SaleSearchHitDTO;
import com.yangaobo.service.ai.SaleAiQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/ai")
public class SaleAiInternalController {

    private final SaleAiQueryService saleAiQueryService;

    public SaleAiInternalController(SaleAiQueryService saleAiQueryService) {
        this.saleAiQueryService = saleAiQueryService;
    }

    @GetMapping("/articles/knowledge")
    public AiCursorPage<ArticleKnowledgeDTO> articleKnowledge(
            @RequestParam(name = "cursor", defaultValue = "0")
            @Min(0) Long cursor,
            @RequestParam(name = "size", defaultValue = "100")
            @Min(1) @Max(200) int size) {
        return saleAiQueryService.articleKnowledge(cursor, size);
    }

    @GetMapping("/notices/knowledge")
    public AiCursorPage<NoticeKnowledgeDTO> noticeKnowledge(
            @RequestParam(name = "cursor", defaultValue = "0")
            @Min(0) Long cursor,
            @RequestParam(name = "size", defaultValue = "100")
            @Min(1) @Max(200) int size) {
        return saleAiQueryService.noticeKnowledge(cursor, size);
    }

    @GetMapping("/articles/{id}")
    public ArticleKnowledgeDTO getArticle(@PathVariable("id") Long id) {
        return saleAiQueryService.getArticle(id);
    }

    @GetMapping("/notices/{id}")
    public NoticeKnowledgeDTO getNotice(@PathVariable("id") Long id) {
        return saleAiQueryService.getNotice(id);
    }

    @GetMapping("/search")
    public List<SaleSearchHitDTO> search(
            @RequestParam(name = "keyword")
            @Size(min = 1, max = 100) String keyword,
            @RequestParam(name = "limit", defaultValue = "12")
            @Min(1) @Max(50) int limit) {
        return saleAiQueryService.search(keyword, limit);
    }
}
