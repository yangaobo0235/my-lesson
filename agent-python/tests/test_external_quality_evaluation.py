from unittest.mock import AsyncMock, MagicMock
from uuid import UUID

from mylesson_agent.agents.routing import PROFILES, AgentRoute, AgentSelection
from mylesson_agent.config import Settings
from mylesson_agent.domain.api_models import UserIntent
from mylesson_agent.infrastructure.orm import KnowledgeChunkRow, KnowledgeSourceRow
from mylesson_agent.llm.client import IntentResult
from mylesson_agent.rag.search_client import KeywordSearchHit
from scripts.run_external_quality_evaluation import (
    ElasticsearchKeywordRetriever,
    arguments_match,
    evaluate_tool,
    source_requirements_met,
)


def test_source_requirements_require_every_explicit_source_reference() -> None:
    assert source_requirements_met(
        expected_types={"COURSE"},
        expected_refs={"COURSE:1", "COURSE:18"},
        actual_types={"COURSE"},
        actual_refs={"COURSE:1", "COURSE:18", "COURSE_EPISODES:1"},
    )
    assert not source_requirements_met(
        expected_types={"COURSE"},
        expected_refs={"COURSE:1", "COURSE:18"},
        actual_types={"COURSE"},
        actual_refs={"COURSE:1", "COURSE:2"},
    )


def test_strict_arguments_reject_unexpected_identity_fields() -> None:
    expected = {"courseId": 7}

    assert arguments_match({"courseId": 7}, expected, strict=True)
    assert not arguments_match(
        {"courseId": 7, "userId": 200},
        expected,
        strict=True,
    )
    assert arguments_match(
        {"courseId": 7, "traceId": "local"},
        expected,
        strict=False,
    )


async def test_tool_evaluation_requires_expected_intent() -> None:
    class StubRouter:
        async def select(self, question: str, context: str) -> AgentSelection:
            del question, context
            return AgentSelection(
                IntentResult(UserIntent.OUT_OF_SCOPE, 1.0, "test"),
                PROFILES[UserIntent.OUT_OF_SCOPE],
                AgentRoute(False, False, ()),
            )

    result = await evaluate_tool(
        {
            "question": "帮我找课程",
            "expectedIntent": "COURSE_SEARCH",
            "expectedTools": [],
        },
        StubRouter(),  # type: ignore[arg-type]
    )

    assert result["passed"] is False
    assert result["metrics"]["intentMatch"] is False
    assert result["metrics"]["actualIntent"] == "OUT_OF_SCOPE"


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
