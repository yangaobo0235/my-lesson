from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Response, status
from fastapi.responses import StreamingResponse
from pydantic import Field
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.api.dependencies import container, conversation_service, database_session
from mylesson_agent.container import Container
from mylesson_agent.conversation.service import ConversationNotFoundError, ConversationService
from mylesson_agent.domain.api_models import (
    ApiModel,
    AuthenticatedUser,
    ConversationView,
    MessageView,
    RunView,
)
from mylesson_agent.security.delegation import require_user

router = APIRouter(prefix="/api/v1/ai")


class CreateConversationRequest(ApiModel):
    title: str | None = Field(default=None, max_length=200)


class SendMessageRequest(ApiModel):
    message: str = Field(min_length=1, max_length=4000)
    request_id: UUID


@router.post("/conversations", response_model=ConversationView, status_code=status.HTTP_201_CREATED)
async def create_conversation(
    request: CreateConversationRequest | None,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
) -> ConversationView:
    title = (
        request.title.strip() if request and request.title and request.title.strip() else "新对话"
    )
    row = await app.repository.create_conversation(session, user.id, title)
    return ConversationView.model_validate(row)


@router.get("/conversations", response_model=list[ConversationView])
async def conversations(
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
) -> list[ConversationView]:
    rows = await app.repository.list_conversations(session, user.id)
    return [ConversationView.model_validate(row) for row in rows]


@router.get("/conversations/{conversation_id}/messages", response_model=list[MessageView])
async def messages(
    conversation_id: UUID,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
    limit: int = Query(default=100, ge=1, le=200),
) -> list[MessageView]:
    if await app.repository.owned(session, conversation_id, user.id) is None:
        raise HTTPException(status_code=404, detail="Conversation not found")
    rows = await app.repository.messages(session, conversation_id, limit)
    return [MessageView.model_validate(row) for row in rows]


@router.post(
    "/conversations/{conversation_id}/messages",
    response_model=RunView,
    status_code=status.HTTP_202_ACCEPTED,
)
async def send_message(
    conversation_id: UUID,
    request: SendMessageRequest,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    service: Annotated[ConversationService, Depends(conversation_service)],
) -> RunView:
    try:
        row = await service.submit(conversation_id, request.request_id, request.message, user)
    except ConversationNotFoundError as exception:
        raise HTTPException(status_code=404, detail="Conversation not found") from exception
    return RunView.model_validate(row)


@router.get("/conversations/{conversation_id}/stream")
async def conversation_stream(
    conversation_id: UUID,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
    last_event_id: Annotated[int | None, Header(alias="Last-Event-ID")] = None,
    after_event_id: int | None = Query(default=None, alias="lastEventId", ge=0),
) -> StreamingResponse:
    if await app.repository.owned(session, conversation_id, user.id) is None:
        raise HTTPException(status_code=404, detail="Conversation not found")
    return StreamingResponse(
        app.event_publisher.stream(
            conversation_id,
            after_event_id=max(last_event_id or 0, after_event_id or 0),
        ),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.delete("/conversations/{conversation_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_conversation(
    conversation_id: UUID,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    session: Annotated[AsyncSession, Depends(database_session)],
    app: Annotated[Container, Depends(container)],
) -> Response:
    if not await app.repository.delete_conversation(session, conversation_id, user.id):
        raise HTTPException(status_code=404, detail="Conversation not found")
    return Response(status_code=status.HTTP_204_NO_CONTENT)
