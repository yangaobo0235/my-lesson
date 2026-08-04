from typing import Any
from uuid import uuid4

from mylesson_agent.agents.routing import AgentProfile, AgentRoute, AgentSelection
from mylesson_agent.agents.runtime import AgentRuntime
from mylesson_agent.domain.api_models import AuthenticatedUser, Citation, UserIntent
from mylesson_agent.llm.client import IntentResult
from mylesson_agent.rag.service import RetrievalResult


class KnowledgeRouter:
    async def select(self, question: str, context: str) -> AgentSelection:
        return AgentSelection(
            IntentResult(UserIntent.KNOWLEDGE_QA, 1.0, "test"),
            AgentProfile("KNOWLEDGE_QA", "知识问答", "test", "test"),
            AgentRoute(True, False, ()),
        )


class EmptyRetriever:
    async def search(self, session: Any, question: str) -> RetrievalResult:
        return RetrievalResult([], True)


class NoTools:
    async def execute(self, name: str, arguments: dict[str, Any], user: Any, trace_id: str) -> Any:
        raise AssertionError("No tool should be called")


class NoModelAnswer:
    async def answer(self, system: str, prompt: str) -> str:
        raise AssertionError("The model must not be called without evidence")


async def test_runtime_refuses_before_model_call_when_retrieval_has_no_evidence() -> None:
    runtime = AgentRuntime(
        KnowledgeRouter(),  # type: ignore[arg-type]
        EmptyRetriever(),  # type: ignore[arg-type]
        NoTools(),  # type: ignore[arg-type]
        NoModelAnswer(),  # type: ignore[arg-type]
    )

    state = await runtime.run(
        session=None,  # type: ignore[arg-type]
        question="没有资料的问题",
        context="",
        user=AuthenticatedUser(
            id=1,
            username="student",
            roles=["STUDENT"],
            delegation_token="token",
        ),
        trace_id="trace",
        request_id=uuid4(),
    )

    assert state["answer"] == "当前知识库中没有足够信息回答这个问题。"


def test_runtime_adds_real_citation_footer_when_model_omits_markers() -> None:
    citations = [
        Citation(
            index=1,
            title="Java 入门",
            excerpt="Java 基础",
            source_url="mylesson://course/1",
            source_type="COURSE",
            source_id="1",
        )
    ]

    answer = AgentRuntime._ensure_citations("可以从基础语法开始。", citations)

    assert answer == "可以从基础语法开始。\n\n来源：[1] Java 入门"
