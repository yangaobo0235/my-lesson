from typing import Annotated, Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.api.dependencies import container, database_session
from mylesson_agent.container import Container
from mylesson_agent.domain.api_models import AuthenticatedUser
from mylesson_agent.security.delegation import require_user

router = APIRouter(prefix="/api/v1/ai/runs")


@router.get("/{run_id}/timeline")
async def timeline(
    run_id: UUID,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
) -> dict[str, Any]:
    result = await app.repository.timeline(session, run_id, user.id)
    if result is None:
        raise HTTPException(status_code=404, detail="Run not found")
    return result


@router.get("/{run_id}/retrieval-trace")
async def retrieval_trace(
    run_id: UUID,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
) -> list[dict[str, Any]]:
    result = await app.repository.retrieval_trace(session, run_id, user.id)
    if result is None:
        raise HTTPException(status_code=404, detail="Run not found")
    return result
