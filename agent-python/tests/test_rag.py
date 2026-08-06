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
    assert citation.source_type == "COURSE"
