import asyncio
from dataclasses import dataclass, field, replace
from typing import Any
from uuid import UUID

from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.config import Settings
from mylesson_agent.domain.api_models import Citation
from mylesson_agent.infrastructure.orm import KnowledgeChunkRow, KnowledgeSourceRow
from mylesson_agent.llm.client import ModelClient
from mylesson_agent.rag.search_client import JavaKnowledgeSearchClient, KeywordSearchHit


@dataclass(frozen=True)
class RetrievalHit:
    chunk_id: str
    title: str
    content: str
    source_url: str
    source_type: str
    source_id: str
    score: float
    content_version: int = 0
    rrf_score: float = 0.0
    vector_score: float | None = None
    keyword_score: float | None = None
    rerank_score: float | None = None
    backends: tuple[str, ...] = ()


@dataclass(frozen=True)
class FusedCandidate:
    chunk_id: str
    rrf_score: float
    ranks: dict[str, int]
    raw_scores: dict[str, float]
    reported_versions: dict[str, int]
    reported_sources: dict[str, tuple[str, str]]


@dataclass(frozen=True)
class RetrievalResult:
    hits: list[RetrievalHit]
    reranked: bool
    rewritten_query: str = ""
    backend_stats: dict[str, Any] = field(default_factory=dict)
    decision: str = "ANSWERED"

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
    rankings: list[tuple[str, list[dict[str, Any]]]],
    *,
    rrf_k: int = 60,
    weights: dict[str, float] | None = None,
) -> list[FusedCandidate]:
    if rrf_k < 1:
        raise ValueError("rrf_k must be positive")
    configured_weights = weights or {}
    fused: dict[str, dict[str, Any]] = {}
    for backend, ranking in rankings:
        weight = configured_weights.get(backend, 1.0)
        for default_rank, row in enumerate(ranking, start=1):
            chunk_id = str(row["chunk_id"])
            rank = int(row.get("rank") or default_rank)
            item = fused.setdefault(
                chunk_id,
                {
                    "rrf_score": 0.0,
                    "ranks": {},
                    "raw_scores": {},
                    "versions": {},
                    "sources": {},
                },
            )
            item["rrf_score"] += weight / (rrf_k + rank)
            item["ranks"][backend] = rank
            item["raw_scores"][backend] = float(row.get("score") or 0.0)
            if row.get("content_version") is not None:
                item["versions"][backend] = int(row["content_version"])
            if row.get("source_type") is not None and row.get("source_id") is not None:
                item["sources"][backend] = (
                    str(row["source_type"]),
                    str(row["source_id"]),
                )
    return sorted(
        [
            FusedCandidate(
                chunk_id=chunk_id,
                rrf_score=float(item["rrf_score"]),
                ranks=dict(item["ranks"]),
                raw_scores=dict(item["raw_scores"]),
                reported_versions=dict(item["versions"]),
                reported_sources=dict(item["sources"]),
            )
            for chunk_id, item in fused.items()
        ],
        key=lambda item: item.rrf_score,
        reverse=True,
    )


class HybridRetriever:
    def __init__(
        self,
        settings: Settings,
        model: ModelClient,
        keyword_search: JavaKnowledgeSearchClient,
    ) -> None:
        self._settings = settings
        self._model = model
        self._keyword_search = keyword_search

    async def search(self, session: AsyncSession, query: str) -> RetrievalResult:
        if not query.strip() or not self._model.configured:
            return RetrievalResult([], False, decision="REFUSED")
        rewritten_query = await self._rewrite(query)
        embedding = (await self._model.embed([rewritten_query]))[0]
        keyword_failed = False

        async def vector_search() -> list[dict[str, Any]]:
            rows = (
                (
                    await session.execute(
                        text(
                            """
                        SELECT kc.id::text AS chunk_id,
                               ks.content_version AS content_version,
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
            return [
                dict(row)
                for row in rows
                if float(row.get("score") or 0.0) >= self._settings.vector_minimum_score
            ]

        async def safe_vector_search() -> tuple[list[dict[str, Any]], bool]:
            try:
                return await vector_search(), False
            except Exception:
                return [], True

        async def safe_keyword_search() -> tuple[list[KeywordSearchHit], bool]:
            try:
                return (
                    await self._keyword_search.search(
                        rewritten_query, self._settings.keyword_top_k
                    ),
                    False,
                )
            except Exception:
                return [], True

        (vector_rows, vector_failed), (keyword_hits, keyword_failed) = await asyncio.gather(
            safe_vector_search(), safe_keyword_search()
        )

        keyword_rows = [self._keyword_row(hit) for hit in keyword_hits]
        fused = reciprocal_rank_fusion(
            [("vector", vector_rows), ("keyword", keyword_rows)],
            rrf_k=self._settings.rrf_k,
            weights={
                "vector": self._settings.rrf_vector_weight,
                "keyword": self._settings.rrf_keyword_weight,
            },
        )[: self._settings.rrf_top_k]
        hits = await self._hydrate(session, fused)
        backend_stats: dict[str, Any] = {
            "vectorCandidates": len(vector_rows),
            "keywordCandidates": len(keyword_rows),
            "fusedCandidates": len(fused),
            "hydratedCandidates": len(hits),
            "keywordFailed": keyword_failed,
            "vectorFailed": vector_failed,
        }
        if not hits:
            return RetrievalResult(
                [],
                False,
                rewritten_query=rewritten_query,
                backend_stats=backend_stats,
                decision="REFUSED",
            )
        try:
            ranked = await self._model.rerank(
                query,
                [hit.content for hit in hits],
                len(hits),
            )
            reranked = [
                replace(
                    hits[index],
                    score=score,
                    rerank_score=score,
                )
                for index, score in ranked
                if 0 <= index < len(hits) and score >= self._settings.rerank_minimum_score
            ]
            if not reranked:
                return RetrievalResult(
                    [],
                    True,
                    rewritten_query=rewritten_query,
                    backend_stats=backend_stats,
                    decision="REFUSED",
                )
            return RetrievalResult(
                reranked[: self._settings.answer_top_n],
                True,
                rewritten_query=rewritten_query,
                backend_stats=backend_stats,
                decision="DEGRADED" if keyword_failed or vector_failed else "ANSWERED",
            )
        except Exception:
            return RetrievalResult(
                hits[: self._settings.answer_top_n],
                False,
                rewritten_query=rewritten_query,
                backend_stats=backend_stats | {"rerankFailed": True},
                decision="DEGRADED",
            )

    async def _rewrite(self, query: str) -> str:
        if not self._settings.query_rewrite_enabled:
            return query.strip()
        try:
            return await self._model.rewrite_query(query)
        except Exception:
            return query.strip()

    async def _hydrate(
        self,
        session: AsyncSession,
        candidates: list[FusedCandidate],
    ) -> list[RetrievalHit]:
        if not candidates:
            return []
        candidate_ids: list[UUID] = []
        valid_candidates: dict[str, FusedCandidate] = {}
        for candidate in candidates:
            try:
                candidate_id = UUID(candidate.chunk_id)
            except ValueError:
                continue
            candidate_ids.append(candidate_id)
            valid_candidates[candidate.chunk_id] = candidate
        if not candidate_ids:
            return []
        rows = await session.execute(
            select(KnowledgeChunkRow, KnowledgeSourceRow)
            .join(KnowledgeSourceRow, KnowledgeSourceRow.id == KnowledgeChunkRow.source_id)
            .where(
                KnowledgeChunkRow.id.in_(candidate_ids),
                KnowledgeSourceRow.status == "ACTIVE",
            )
        )
        hydrated: dict[str, RetrievalHit] = {}
        for chunk, source in rows.tuples():
            hydrated_candidate = valid_candidates.get(str(chunk.id))
            if hydrated_candidate is None:
                continue
            keyword_version = hydrated_candidate.reported_versions.get("keyword")
            if keyword_version is not None and source.content_version < keyword_version:
                continue
            if not self._source_matches(
                hydrated_candidate,
                source.source_type,
                source.source_id,
            ):
                continue
            hydrated[str(chunk.id)] = RetrievalHit(
                chunk_id=str(chunk.id),
                title=chunk.title,
                content=chunk.content,
                source_url=source.source_url,
                source_type=source.source_type,
                source_id=source.source_id,
                score=hydrated_candidate.rrf_score,
                content_version=source.content_version,
                rrf_score=hydrated_candidate.rrf_score,
                vector_score=hydrated_candidate.raw_scores.get("vector"),
                keyword_score=hydrated_candidate.raw_scores.get("keyword"),
                backends=tuple(sorted(hydrated_candidate.ranks)),
            )
        return [hydrated[item.chunk_id] for item in candidates if item.chunk_id in hydrated]

    @staticmethod
    def _keyword_row(hit: KeywordSearchHit) -> dict[str, Any]:
        return {
            "chunk_id": hit.chunk_id,
            "rank": hit.rank,
            "score": hit.score,
            "content_version": hit.content_version,
            "source_type": hit.source_type,
            "source_id": hit.source_id,
        }

    @staticmethod
    def _source_matches(
        candidate: FusedCandidate,
        source_type: str,
        source_id: str,
    ) -> bool:
        reported = candidate.reported_sources.get("keyword")
        return reported is None or reported == (source_type, source_id)
