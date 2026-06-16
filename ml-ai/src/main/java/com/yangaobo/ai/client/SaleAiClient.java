package com.yangaobo.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@FeignClient(name = "ml-sale", contextId = "saleAiClient")
public interface SaleAiClient {

    @GetMapping("/internal/ai/articles/knowledge")
    InternalAiResponse<CursorPage<ArticleKnowledge>> articleKnowledge(
            @RequestParam Long cursor,
            @RequestParam int size);

    @GetMapping("/internal/ai/notices/knowledge")
    InternalAiResponse<CursorPage<NoticeKnowledge>> noticeKnowledge(
            @RequestParam Long cursor,
            @RequestParam int size);

    @GetMapping("/internal/ai/articles/{id}")
    InternalAiResponse<ArticleKnowledge> getArticle(@PathVariable Long id);

    @GetMapping("/internal/ai/notices/{id}")
    InternalAiResponse<NoticeKnowledge> getNotice(@PathVariable Long id);

    @GetMapping("/internal/ai/search")
    InternalAiResponse<List<SaleSearchHit>> search(
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "limit") int limit);

    record ArticleKnowledge(
            Long id,
            String title,
            String content,
            LocalDateTime updated
    ) {
    }

    record NoticeKnowledge(
            Long id,
            String content,
            LocalDateTime updated
    ) {
    }

    record SaleSearchHit(
            String sourceType,
            Long id,
            String title,
            String snippet,
            LocalDateTime updated
    ) {
    }

    record CursorPage<T>(List<T> items, Long nextCursor, boolean hasMore) {
    }
}
