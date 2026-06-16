package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.model.SearchHit;

import java.util.List;

public interface RerankService {

    List<SearchHit> rerank(
            String query,
            List<SearchHit> candidates,
            int topN);
}
