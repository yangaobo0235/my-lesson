from datetime import UTC, datetime
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.agents.routing import IntentRouter
from mylesson_agent.infrastructure.orm import EvaluationRunRow

CASES = (
    ("什么是Java泛型", "KNOWLEDGE_QA"),
    ("推荐Java课程", "COURSE_SEARCH"),
    ("查看我的订单", "PERSONAL_QUERY"),
    ("制定每天30分钟学习计划", "LEARNING_PLAN"),
    ("把Java课程加入购物车", "OUT_OF_SCOPE"),
)


class EvaluationService:
    def __init__(self, router: IntentRouter) -> None:
        self._router = router

    async def run(self, session: AsyncSession, mode: str) -> EvaluationRunRow:
        if mode not in {"deterministic", "external"}:
            raise ValueError("Evaluation mode must be deterministic or external")
        row = EvaluationRunRow(mode=mode, status="RUNNING", summary={}, report={})
        session.add(row)
        await session.flush()
        results = []
        passed = 0
        for question, expected in CASES:
            selection = await self._router.select(question, "")
            actual = selection.decision.intent.value
            case_passed = actual == expected
            passed += int(case_passed)
            results.append(
                {
                    "question": question,
                    "expectedIntent": expected,
                    "actualIntent": actual,
                    "passed": case_passed,
                }
            )
        row.status = "SUCCEEDED"
        row.summary = {
            "total": len(CASES),
            "passed": passed,
            "failed": len(CASES) - passed,
            "passRate": passed / len(CASES),
        }
        row.report = {"id": str(row.id), "mode": mode, "summary": row.summary, "results": results}
        row.finished_at = datetime.now(UTC)
        await session.commit()
        await session.refresh(row)
        return row

    async def find(self, session: AsyncSession, run_id: UUID) -> EvaluationRunRow | None:
        row = await session.scalar(select(EvaluationRunRow).where(EvaluationRunRow.id == run_id))
        return row

    async def recent(self, session: AsyncSession, limit: int) -> list[EvaluationRunRow]:
        return list(
            await session.scalars(
                select(EvaluationRunRow).order_by(EvaluationRunRow.created_at.desc()).limit(limit)
            )
        )
