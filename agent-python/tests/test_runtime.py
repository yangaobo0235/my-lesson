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


class EvidenceRetriever:
    async def search(self, session: Any, question: str) -> RetrievalResult:
        from mylesson_agent.rag.service import RetrievalHit

        return RetrievalResult(
            [
                RetrievalHit(
                    chunk_id="chunk-1",
                    title="Java 入门",
                    content="Java 支持面向对象编程。",
                    source_url="mylesson://course/1",
                    source_type="COURSE",
                    source_id="1",
                    score=1.0,
                    content_version=2,
                )
            ],
            False,
        )


class GroundedModel:
    def __init__(self, supported: bool) -> None:
        self.supported = supported

    async def answer(self, system: str, prompt: str) -> str:
        return "Java 支持面向对象编程。[1]"

    async def validate_grounding(self, answer: str, citations: list[Citation]) -> Any:
        from mylesson_agent.llm.client import GroundingResult

        return GroundingResult(self.supported, () if self.supported else ("过度推断",), "test")


class CitationRepairModel:
    def __init__(self, answers: list[str]) -> None:
        self.answers = answers
        self.prompts: list[str] = []
        self.grounding_calls = 0

    async def answer(self, system: str, prompt: str) -> str:
        self.prompts.append(prompt)
        return self.answers[len(self.prompts) - 1]

    async def validate_grounding(self, answer: str, citations: list[Citation]) -> Any:
        from mylesson_agent.llm.client import GroundingResult

        self.grounding_calls += 1
        return GroundingResult(True, (), "test")


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


def test_runtime_refuses_when_model_omits_citation_markers() -> None:
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

    assert answer == "当前知识库证据不足，无法生成带有可验证引用的回答。"


def test_runtime_rejects_unknown_citation_number() -> None:
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

    answer = AgentRuntime._ensure_citations("结论来自不存在的资料。[2]", citations)

    assert answer == "当前知识库证据不足，无法生成带有可验证引用的回答。"


def test_runtime_accepts_whitelisted_citation_number() -> None:
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

    answer = AgentRuntime._ensure_citations("可以从基础语法开始。[1]", citations)

    assert answer == "可以从基础语法开始。[1]"


async def test_runtime_returns_answer_only_after_online_grounding_check() -> None:
    runtime = AgentRuntime(
        KnowledgeRouter(),  # type: ignore[arg-type]
        EvidenceRetriever(),  # type: ignore[arg-type]
        NoTools(),  # type: ignore[arg-type]
        GroundedModel(True),  # type: ignore[arg-type]
    )

    state = await runtime.run(
        session=None,  # type: ignore[arg-type]
        question="Java 支持什么编程范式？",
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

    assert state["answer"] == "Java 支持面向对象编程。[1]"


async def test_runtime_retries_once_when_model_omits_citation_markers() -> None:
    model = CitationRepairModel(
        [
            "建议从 Java 入门开始。",
            "建议从 Java 入门开始，因为课程覆盖面向对象编程。[1]",
        ]
    )
    runtime = AgentRuntime(
        KnowledgeRouter(),  # type: ignore[arg-type]
        EvidenceRetriever(),  # type: ignore[arg-type]
        NoTools(),  # type: ignore[arg-type]
        model,  # type: ignore[arg-type]
    )

    state = await runtime.run(
        session=None,  # type: ignore[arg-type]
        question="推荐一门 Java 入门课",
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

    assert state["answer"] == "建议从 Java 入门开始，因为课程覆盖面向对象编程。[1]"
    assert len(model.prompts) == 2
    assert "引用要求" in model.prompts[0]
    assert "上一版回答未通过引用格式校验" in model.prompts[1]
    assert model.grounding_calls == 1


async def test_runtime_refuses_when_citation_retry_is_still_invalid() -> None:
    model = CitationRepairModel(["第一次没有引用。", "第二次仍然没有引用。"])
    runtime = AgentRuntime(
        KnowledgeRouter(),  # type: ignore[arg-type]
        EvidenceRetriever(),  # type: ignore[arg-type]
        NoTools(),  # type: ignore[arg-type]
        model,  # type: ignore[arg-type]
    )

    state = await runtime.run(
        session=None,  # type: ignore[arg-type]
        question="推荐一门 Java 入门课",
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

    assert state["answer"] == "当前知识库证据不足，无法生成带有可验证引用的回答。"
    assert len(model.prompts) == 2
    assert model.grounding_calls == 0


async def test_runtime_refuses_when_online_grounding_check_fails() -> None:
    runtime = AgentRuntime(
        KnowledgeRouter(),  # type: ignore[arg-type]
        EvidenceRetriever(),  # type: ignore[arg-type]
        NoTools(),  # type: ignore[arg-type]
        GroundedModel(False),  # type: ignore[arg-type]
    )

    state = await runtime.run(
        session=None,  # type: ignore[arg-type]
        question="Java 支持什么编程范式？",
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

    assert state["answer"] == "当前知识库证据不足，无法支持回答中的全部结论。"
