from mylesson_agent.rag.service import (
    HybridRetriever,
    RetrievalHit,
    RetrievalResult,
    reciprocal_rank_fusion,
)


def row(chunk_id: str, score: float, rank: int | None = None) -> dict[str, object]:
    value: dict[str, object] = {"chunk_id": chunk_id, "score": score}
    if rank is not None:
        value["rank"] = rank
    return value


def test_rrf_fuses_by_rank_without_comparing_backend_scores() -> None:
    candidates = reciprocal_rank_fusion(
        [
            ("vector", [row("shared", 0.8), row("vector", 0.7)]),
            ("keyword", [row("shared", 18.0), row("keyword", 12.0)]),
        ],
        rrf_k=60,
    )
    assert [item.chunk_id for item in candidates] == ["shared", "vector", "keyword"]
    assert candidates[0].raw_scores == {"vector": 0.8, "keyword": 18.0}
    assert candidates[0].rrf_score > candidates[1].rrf_score


def test_rrf_supports_backend_weights() -> None:
    candidates = reciprocal_rank_fusion(
        [
            ("vector", [row("vector", 0.8)]),
            ("keyword", [row("keyword", 99.0)]),
        ],
        weights={"vector": 2.0, "keyword": 1.0},
    )
    assert [item.chunk_id for item in candidates] == ["vector", "keyword"]


def test_keyword_source_identity_is_preserved_and_validated() -> None:
    candidates = reciprocal_rank_fusion(
        [
            (
                "keyword",
                [
                    {
                        "chunk_id": "shared",
                        "score": 8.0,
                        "source_type": "COURSE",
                        "source_id": "10",
                    }
                ],
            )
        ]
    )

    assert HybridRetriever._source_matches(candidates[0], "COURSE", "10")
    assert not HybridRetriever._source_matches(candidates[0], "COURSE", "11")


def test_keyword_old_content_version_is_rejected_during_postgres_hydration() -> None:
    candidates = reciprocal_rank_fusion(
        [
            (
                "keyword",
                [
                    {
                        "chunk_id": "shared",
                        "score": 8.0,
                        "content_version": 4,
                    }
                ],
            )
        ]
    )

    assert HybridRetriever._content_version_matches(candidates[0], 4)
    assert not HybridRetriever._content_version_matches(candidates[0], 5)


def test_citations_are_numbered_from_final_hits() -> None:
    result = RetrievalResult(
        [
            RetrievalHit(
                chunk_id="one",
                title="title-one",
                content="content",
                source_url="mylesson://course/1",
                source_type="COURSE",
                source_id="1",
                score=0.9,
            )
        ],
        False,
    )
    citation = result.citations(1)[0]
    assert citation.index == 1
    assert citation.chunk_id == "one"
    assert citation.content_version == 0
    assert citation.source_type == "COURSE"


def test_final_hits_limit_repeated_chunks_from_one_source() -> None:
    hits = [
        RetrievalHit(
            chunk_id=str(index),
            title=f"title-{index}",
            content="content",
            source_url=f"mylesson://course/{source_id}",
            source_type="COURSE",
            source_id=source_id,
            score=1.0 - index / 10,
        )
        for index, source_id in enumerate(("1", "1", "1", "2", "3"), start=1)
    ]

    covered = HybridRetriever._cover_sources(hits, limit=4, max_per_source=2)

    assert [hit.chunk_id for hit in covered] == ["1", "2", "4", "5"]
