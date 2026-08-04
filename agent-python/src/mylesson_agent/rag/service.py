from dataclasses import dataclass
from typing import Any

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.config import Settings
from mylesson_agent.domain.api_models import Citation
from mylesson_agent.llm.client import ModelClient


@dataclass(frozen=True)
class RetrievalHit:
    chunk_id: str
    title: str
    content: str
    source_url: str
    source_type: str
    source_id: str
    score: float


@dataclass(frozen=True)
class RetrievalResult:
    hits: list[RetrievalHit]
    reranked: bool

    def citations(self, limit: int) -> list[Citation]:
        return [
            Citation(
                index=index,
                title=hit.title,
                excerpt=hit.content[:500],
                source_url=hit.source_url,
                source_type=hit.source_type,
                source_id=hit.source_id,
            )
            for index, hit in enumerate(self.hits[:limit], start=1)
        ]


def reciprocal_rank_fusion(
    rankings: list[list[dict[str, Any]]],
    minimum_relevant_score: float,
) -> list[RetrievalHit]:
    fused: dict[str, tuple[dict[str, Any], float, float]] = {}
    for ranking in rankings:
        for rank, row in enumerate(ranking, start=1):
            key = str(row["chunk_id"])
            previous = fused.get(key, (dict(row), 0.0, 0.0))
            raw_score = float(row.get("score") or 0.0)
            fused[key] = (
                previous[0],
                previous[1] + 1.0 / (60 + rank),
                max(previous[2], raw_score),
            )
    ordered = sorted(fused.values(), key=lambda item: item[1], reverse=True)
    return [
        RetrievalHit(
            chunk_id=str(row["chunk_id"]),
            title=str(row["title"]),
            content=str(row["content"]),
            source_url=str(row["source_url"]),
            source_type=str(row["source_type"]),
            source_id=str(row["source_id"]),
            score=float(fused_score),
        )
        for row, fused_score, relevance_score in ordered
        if relevance_score >= minimum_relevant_score
    ]


class HybridRetriever:
    def __init__(self, settings: Settings, model: ModelClient) -> None:
        self._settings = settings
        self._model = model

    async def search(self, session: AsyncSession, query: str) -> RetrievalResult:
        if not query.strip() or not self._model.configured:
            return RetrievalResult([], False)
        embedding = (await self._model.embed([query]))[0]
        vector_rows = (
            (
                await session.execute(
                    text(
                        """
                    SELECT kc.id::text AS chunk_id, kc.title, kc.content,
                           ks.source_url, ks.source_type, ks.source_id,
                           1 - (kc.embedding <=> CAST(:embedding AS vector)) AS score
                    FROM knowledge_chunk kc
                    JOIN knowledge_source ks ON ks.id = kc.source_id
                    WHERE ks.status = 'ACTIVE'
                    ORDER BY kc.embedding <=> CAST(:embedding AS vector)
                    LIMIT :limit
                    """
                    ),
                    {"embedding": str(embedding), "limit": self._settings.vector_top_k},
                )
            )
            .mappings()
            .all()
        )
        keyword_rows = (
            (
                await session.execute(
                    text(
                        """
                    SELECT kc.id::text AS chunk_id, kc.title, kc.content,
                           ks.source_url, ks.source_type, ks.source_id,
                           similarity(kc.content, :query) AS score
                    FROM knowledge_chunk kc
                    JOIN knowledge_source ks ON ks.id = kc.source_id
                    WHERE ks.status = 'ACTIVE' AND similarity(kc.content, :query) > 0
                    ORDER BY score DESC
                    LIMIT :limit
                    """
                    ),
                    {"query": query, "limit": self._settings.keyword_top_k},
                )
            )
            .mappings()
            .all()
        )
        hits = reciprocal_rank_fusion(
            [list(map(dict, vector_rows)), list(map(dict, keyword_rows))],
            self._settings.minimum_relevant_score,
        )
        if not hits:
            return RetrievalResult([], False)
        try:
            ranked = await self._model.rerank(
                query,
                [hit.content for hit in hits],
                len(hits),
            )
            reranked = [
                RetrievalHit(
                    chunk_id=hits[index].chunk_id,
                    title=hits[index].title,
                    content=hits[index].content,
                    source_url=hits[index].source_url,
                    source_type=hits[index].source_type,
                    source_id=hits[index].source_id,
                    score=score,
                )
                for index, score in ranked
                if 0 <= index < len(hits) and score >= self._settings.rerank_minimum_score
            ]
            return RetrievalResult(reranked[: self._settings.answer_top_n], True)
        except Exception:
            return RetrievalResult(hits[: self._settings.answer_top_n], False)
