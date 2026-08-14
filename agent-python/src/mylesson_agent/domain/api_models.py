from datetime import datetime
from enum import StrEnum
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        serialize_by_alias=True,
        from_attributes=True,
    )


class UserIntent(StrEnum):
    KNOWLEDGE_QA = "KNOWLEDGE_QA"
    COURSE_SEARCH = "COURSE_SEARCH"
    PERSONAL_QUERY = "PERSONAL_QUERY"
    LEARNING_PLAN = "LEARNING_PLAN"
    OUT_OF_SCOPE = "OUT_OF_SCOPE"


class ConversationView(ApiModel):
    id: UUID
    user_id: int
    title: str
    status: str
    created_at: datetime
    updated_at: datetime


class MessageView(ApiModel):
    id: UUID
    conversation_id: UUID
    role: str
    content: str
    citations: list[dict[str, Any]] = Field(default_factory=list)
    request_id: UUID | None = None
    trace_id: str | None = None
    summary_until: datetime | None = None
    summary_until_message_id: UUID | None = None
    created_at: datetime


class RunView(ApiModel):
    id: UUID
    conversation_id: UUID
    request_id: UUID
    status: str
    user_message_id: UUID
    assistant_message_id: UUID | None = None
    trace_id: str
    error_message: str | None = None


class StreamEvent(ApiModel):
    event_id: int
    sequence: int
    type: str
    conversation_id: UUID
    run_id: UUID
    request_id: UUID
    trace_id: str
    timestamp: datetime
    data: dict[str, Any]


class Citation(ApiModel):
    index: int
    chunk_id: str | None = None
    content_version: int | None = None
    title: str
    excerpt: str
    source_url: str
    source_type: str | None = None
    source_id: str | None = None


class AgentAnswer(ApiModel):
    answer: str
    citations: list[Citation] = Field(default_factory=list)
    trace_id: str


class AuthenticatedUser(ApiModel):
    id: int
    username: str
    roles: list[str]
    delegation_token: str


class ErrorResponse(ApiModel):
    code: str
    message: str
    retryable: bool = False
    trace_id: str | None = None
    details: dict[str, Any] | None = None
