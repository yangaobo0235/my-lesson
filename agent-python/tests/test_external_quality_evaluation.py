from unittest.mock import AsyncMock, MagicMock
from uuid import UUID

from mylesson_agent.config import Settings
from mylesson_agent.infrastructure.orm import KnowledgeChunkRow, KnowledgeSourceRow
from mylesson_agent.rag.search_client import KeywordSearchHit
from scripts.run_external_quality_evaluation import ElasticsearchKeywordRetriever


async def test_elasticsearch_keyword_retriever_hydrates_only_matching_source() -> None:
    chunk_id = UUID("00000000-0000-0000-0000-000000000001")
    source_row_id = UUID("00000000-0000-0000-0000-000000000002")
    source = KnowledgeSourceRow(
        id=source_row_id,
        source_type="COURSE",
        source_id="10",
        title="清晨跑步",
        source_url="mylesson://course/10",
        content_hash="hash",
        content_version=2,
        status="ACTIVE",
    )
    chunk = KnowledgeChunkRow(
        id=chunk_id,
        source_id=source_row_id,
        chunk_index=0,
        title="清晨跑步",
        content="从走跑交替开始。",
        metadata_json={},
        embedding=[0.0] * 1024,
    )
    keyword_search = MagicMock()
    keyword_search.search = AsyncMock(
        return_value=[
            KeywordSearchHit(str(chunk_id), 1, 8.4, "COURSE", "10", 2),
            KeywordSearchHit(str(chunk_id), 2, 7.1, "COURSE", "11", 2),
        ]
    )
    rows = MagicMock()
    rows.tuples.return_value = [(chunk, source)]
    session = AsyncMock()
    session.execute.return_value = rows

    result = await ElasticsearchKeywordRetriever(Settings(), keyword_search).search(
        session,
        "清晨跑步",
    )

    assert result.decision == "ANSWERED"
    assert len(result.hits) == 1
    assert result.hits[0].keyword_score == 8.4
    assert result.hits[0].backends == ("keyword",)
