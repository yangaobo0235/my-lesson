package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.model.SearchHit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoOpRerankService implements RerankService {

    @Override
    public List<SearchHit> rerank(
            String query,
            List<SearchHit> candidates,
            int topN) {
        return candidates.stream()
                .limit(topN)
                .toList();
    }
}
