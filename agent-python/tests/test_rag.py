from mylesson_agent.rag.service import reciprocal_rank_fusion


def row(chunk_id: str, score: float) -> dict[str, object]:
    return {
        "chunk_id": chunk_id,
        "title": f"title-{chunk_id}",
        "content": "content",
        "source_url": "mylesson://course/1",
        "source_type": "COURSE",
        "source_id": "1",
        "score": score,
    }


def test_rrf_uses_raw_relevance_for_threshold_and_fused_score_for_order() -> None:
    hits = reciprocal_rank_fusion(
        [[row("shared", 0.8), row("vector", 0.7)], [row("shared", 0.1), row("weak", 0.2)]],
        0.55,
    )
    assert [hit.chunk_id for hit in hits] == ["shared", "vector"]
    assert hits[0].score > hits[1].score


def test_citations_are_numbered_from_fused_hits() -> None:
    from mylesson_agent.rag.service import RetrievalResult

    result = RetrievalResult(reciprocal_rank_fusion([[row("one", 0.9)]], 0.55), False)
    citation = result.citations(1)[0]
    assert citation.index == 1
    assert citation.source_type == "COURSE"
