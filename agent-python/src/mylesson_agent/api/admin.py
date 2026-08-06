from typing import Annotated, Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.api.dependencies import container, database_session
from mylesson_agent.container import Container
from mylesson_agent.domain.api_models import AuthenticatedUser
from mylesson_agent.evaluation.service import EvaluationService
from mylesson_agent.infrastructure.orm import KnowledgeEventRow, KnowledgeSourceRow
from mylesson_agent.knowledge.events import KnowledgeEvent, KnowledgeEventConsumer
from mylesson_agent.security.delegation import ensure_admin, require_user

router = APIRouter(prefix="/api/v1/ai")


@router.get("/admin/knowledge/status")
async def knowledge_status(
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
) -> dict[str, Any]:
    ensure_admin(user)
    total = await session.scalar(select(func.count()).select_from(KnowledgeSourceRow))
    active = await session.scalar(
        select(func.count())
        .select_from(KnowledgeSourceRow)
        .where(KnowledgeSourceRow.status == "ACTIVE")
    )
    failed = await session.scalar(
        select(func.count())
        .select_from(KnowledgeSourceRow)
        .where(KnowledgeSourceRow.status == "FAILED")
    )
    return {
        "status": "READY" if failed == 0 else "DEGRADED",
        "totalSources": total or 0,
        "activeSources": active or 0,
        "failedSources": failed or 0,
    }


@router.get("/admin/knowledge/sources")
async def knowledge_sources(
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    source_type: str | None = Query(default=None, alias="sourceType"),
    source_status: str | None = Query(default=None, alias="status"),
    limit: int = Query(default=100, ge=1, le=200),
) -> list[dict[str, Any]]:
    ensure_admin(user)
    query = select(KnowledgeSourceRow)
    if source_type:
        query = query.where(KnowledgeSourceRow.source_type == source_type)
    if source_status:
        query = query.where(KnowledgeSourceRow.status == source_status)
    rows = list(
        await session.scalars(query.order_by(KnowledgeSourceRow.updated_at.desc()).limit(limit))
    )
    return [
        {
            "sourceType": row.source_type,
            "sourceId": row.source_id,
            "title": row.title,
            "sourceUrl": row.source_url,
            "status": row.status,
            "contentVersion": row.content_version,
            "esIndexedVersion": row.es_indexed_version,
            "esIndexedAt": row.es_indexed_at,
            "esIndexError": row.es_index_error,
            "indexedAt": row.indexed_at,
            "updatedAt": row.updated_at,
            "errorMessage": row.error_message,
        }
        for row in rows
    ]


@router.post("/admin/knowledge/sources/{source_type}/{source_id}/retry")
async def retry_source(
    source_type: str,
    source_id: str,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
) -> dict[str, Any]:
    ensure_admin(user)
    row = await session.scalar(
        select(KnowledgeSourceRow).where(
            KnowledgeSourceRow.source_type == source_type,
            KnowledgeSourceRow.source_id == source_id,
        )
    )
    if row is None:
        raise HTTPException(status_code=404, detail="Knowledge source not found")
    event = await session.scalar(
        select(KnowledgeEventRow)
        .where(
            KnowledgeEventRow.source_type == source_type,
            KnowledgeEventRow.source_id == source_id,
            KnowledgeEventRow.status == "FAILED",
        )
        .order_by(KnowledgeEventRow.received_at.desc())
        .limit(1)
    )
    if event is None:
        raise HTTPException(status_code=409, detail="No failed knowledge event to retry")
    consumer = KnowledgeEventConsumer(app.settings, app.knowledge_indexer)
    return await consumer.consume(
        session,
        KnowledgeEvent(
            event_id=event.event_id,
            event_type=event.event_type,
            aggregate_id=event.source_id,
            version=event.content_version,
        ),
    )


@router.post("/admin/evaluations/run")
async def run_evaluation(
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
    mode: str = Query(default="deterministic"),
) -> dict[str, Any]:
    ensure_admin(user)
    try:
        row = await EvaluationService(app.router).run(session, mode)
    except ValueError as exception:
        raise HTTPException(status_code=400, detail=str(exception)) from exception
    return row.report


@router.get("/admin/evaluations/{run_id}")
async def evaluation_report(
    run_id: UUID,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
) -> dict[str, Any]:
    ensure_admin(user)
    row = await EvaluationService(app.router).find(session, run_id)
    if row is None:
        raise HTTPException(status_code=404, detail="Evaluation report not found")
    return row.report


@router.get("/admin/evaluation/summary")
async def evaluation_summary(
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
) -> dict[str, Any]:
    ensure_admin(user)
    rows = await EvaluationService(app.router).recent(session, 100)
    total = len(rows)
    succeeded = sum(row.status == "SUCCEEDED" for row in rows)
    return {"totalRuns": total, "successfulRuns": succeeded, "failedRuns": total - succeeded}


@router.get("/admin/evaluation/results")
async def evaluation_results(
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
    limit: int = Query(default=100, ge=1, le=200),
) -> list[dict[str, Any]]:
    ensure_admin(user)
    rows = await EvaluationService(app.router).recent(session, limit)
    return [row.report for row in rows]


@router.get("/admin/tools/calls")
async def tool_calls(
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
    limit: int = Query(default=100, ge=1, le=200),
) -> list[dict[str, Any]]:
    ensure_admin(user)
    return await app.repository.tool_calls(session, user.id, limit)
