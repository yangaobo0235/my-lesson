from typing import Annotated, Any
from uuid import UUID

from fastapi import APIRouter, Depends
from pydantic import Field
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.api.dependencies import container, database_session
from mylesson_agent.container import Container
from mylesson_agent.domain.api_models import ApiModel
from mylesson_agent.knowledge.events import KnowledgeEvent, KnowledgeEventConsumer
from mylesson_agent.security.delegation import require_service

router = APIRouter(prefix="/internal/v1/knowledge")


class KnowledgeEventRequest(ApiModel):
    event_id: UUID
    event_type: str = Field(min_length=1, max_length=64)
    aggregate_id: str = Field(min_length=1, max_length=100)
    version: int = Field(ge=1)


class KnowledgeUpsertRequest(ApiModel):
    source_type: str = Field(min_length=1, max_length=32)
    source_id: str = Field(min_length=1, max_length=100)
    title: str = Field(min_length=1, max_length=500)
    source_url: str = Field(min_length=1, max_length=1000)
    content: str = Field(min_length=1, max_length=1_000_000)
    content_version: int = Field(default=1, ge=1)
    metadata: dict[str, Any] = Field(default_factory=dict)


@router.post("/events")
async def consume_knowledge_event(
    request: KnowledgeEventRequest,
    _service: Annotated[None, Depends(require_service)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
) -> dict[str, Any]:
    consumer = KnowledgeEventConsumer(app.settings, app.knowledge_indexer)
    return await consumer.consume(
        session,
        KnowledgeEvent(
            event_id=request.event_id,
            event_type=request.event_type,
            aggregate_id=request.aggregate_id,
            version=request.version,
        ),
    )


@router.post("/upsert")
async def upsert_knowledge(
    request: KnowledgeUpsertRequest,
    _service: Annotated[None, Depends(require_service)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
) -> dict[str, Any]:
    row = await app.knowledge_indexer.upsert(session, **request.model_dump())
    await session.commit()
    await session.refresh(row)
    return {"sourceType": row.source_type, "sourceId": row.source_id, "status": row.status}
