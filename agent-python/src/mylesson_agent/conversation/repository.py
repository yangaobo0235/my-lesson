from datetime import UTC, datetime
from typing import Any, cast
from uuid import UUID

from sqlalchemy import delete, exists, func, or_, select, update
from sqlalchemy.engine import CursorResult
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import aliased

from mylesson_agent.infrastructure.orm import (
    AgentRunRow,
    ConversationRow,
    MessageRow,
    RetrievalTraceRow,
    RunEventRow,
    ToolCallRow,
)
from mylesson_agent.security.redaction import sanitize_audit_payload


class ConversationRepository:
    async def create_conversation(
        self, session: AsyncSession, user_id: int, title: str
    ) -> ConversationRow:
        row = ConversationRow(user_id=user_id, title=title)
        session.add(row)
        await session.commit()
        await session.refresh(row)
        return row

    async def list_conversations(
        self, session: AsyncSession, user_id: int
    ) -> list[ConversationRow]:
        result = await session.scalars(
            select(ConversationRow)
            .where(ConversationRow.user_id == user_id, ConversationRow.status == "ACTIVE")
            .order_by(ConversationRow.updated_at.desc())
        )
        return list(result)

    async def owned(
        self, session: AsyncSession, conversation_id: UUID, user_id: int
    ) -> ConversationRow | None:
        statement = select(ConversationRow).where(
            ConversationRow.id == conversation_id,
            ConversationRow.user_id == user_id,
            ConversationRow.status == "ACTIVE",
        )
        return cast(ConversationRow | None, await session.scalar(statement))

    async def messages(
        self, session: AsyncSession, conversation_id: UUID, limit: int
    ) -> list[MessageRow]:
        rows = await session.scalars(
            select(MessageRow)
            .where(MessageRow.conversation_id == conversation_id)
            .order_by(MessageRow.created_at.desc())
            .limit(limit)
        )
        return list(reversed(list(rows)))

    async def delete_conversation(
        self, session: AsyncSession, conversation_id: UUID, user_id: int
    ) -> bool:
        result = cast(
            CursorResult[Any],
            await session.execute(
                update(ConversationRow)
                .where(
                    ConversationRow.id == conversation_id,
                    ConversationRow.user_id == user_id,
                    ConversationRow.status == "ACTIVE",
                )
                .values(status="DELETED", updated_at=datetime.now(UTC))
            ),
        )
        await session.commit()
        return bool(result.rowcount)

    async def find_run_by_request(
        self, session: AsyncSession, conversation_id: UUID, request_id: UUID
    ) -> AgentRunRow | None:
        statement = select(AgentRunRow).where(
            AgentRunRow.conversation_id == conversation_id,
            AgentRunRow.request_id == request_id,
        )
        return cast(AgentRunRow | None, await session.scalar(statement))

    async def create_submission(
        self,
        session: AsyncSession,
        conversation: ConversationRow,
        request_id: UUID,
        message: str,
        trace_id: str,
        username: str,
        user_roles: list[str],
    ) -> AgentRunRow:
        user_message = MessageRow(
            conversation_id=conversation.id,
            role="USER",
            content=message,
            request_id=request_id,
            trace_id=trace_id,
        )
        session.add(user_message)
        await session.flush()
        run = AgentRunRow(
            conversation_id=conversation.id,
            user_id=conversation.user_id,
            username=username,
            user_roles=user_roles,
            request_id=request_id,
            user_message_id=user_message.id,
            status="QUEUED",
            trace_id=trace_id,
        )
        session.add(run)
        conversation.updated_at = datetime.now(UTC)
        await session.commit()
        await session.refresh(run)
        return run

    async def claim_next(
        self, session: AsyncSession, worker_id: str
    ) -> tuple[AgentRunRow, MessageRow] | None:
        active_run = aliased(AgentRunRow)
        result = await session.execute(
            select(AgentRunRow, MessageRow)
            .join(MessageRow, MessageRow.id == AgentRunRow.user_message_id)
            .join(ConversationRow, ConversationRow.id == AgentRunRow.conversation_id)
            .where(
                AgentRunRow.status == "QUEUED",
                ~exists(
                    select(active_run.id).where(
                        active_run.conversation_id == AgentRunRow.conversation_id,
                        active_run.status == "RUNNING",
                    )
                ),
            )
            .order_by(AgentRunRow.created_at)
            .limit(1)
            .with_for_update(of=(AgentRunRow, ConversationRow), skip_locked=True)
        )
        claimed = result.first()
        if claimed is None:
            await session.rollback()
            return None
        run, message = claimed
        run.status = "RUNNING"
        run.worker_id = worker_id
        run.heartbeat_at = datetime.now(UTC)
        run.attempts += 1
        await session.commit()
        return run, message

    async def requeue(self, session: AsyncSession, run_id: UUID) -> None:
        await session.execute(
            update(AgentRunRow)
            .where(AgentRunRow.id == run_id, AgentRunRow.status == "RUNNING")
            .values(status="QUEUED", worker_id=None, heartbeat_at=None)
        )
        await session.commit()

    async def recover_stale(
        self, session: AsyncSession, stale_before: datetime
    ) -> int:
        result = cast(
            CursorResult[Any],
            await session.execute(
                update(AgentRunRow)
                .where(
                    AgentRunRow.status == "RUNNING",
                    or_(
                        AgentRunRow.heartbeat_at.is_(None),
                        AgentRunRow.heartbeat_at < stale_before,
                    ),
                )
                .values(
                    status="QUEUED",
                    worker_id=None,
                    heartbeat_at=None,
                )
            ),
        )
        await session.commit()
        return int(result.rowcount or 0)

    async def heartbeat(self, session: AsyncSession, run_id: UUID, worker_id: str) -> bool:
        result = cast(
            CursorResult[Any],
            await session.execute(
                update(AgentRunRow)
                .where(
                    AgentRunRow.id == run_id,
                    AgentRunRow.status == "RUNNING",
                    AgentRunRow.worker_id == worker_id,
                )
                .values(heartbeat_at=datetime.now(UTC))
            ),
        )
        await session.commit()
        return bool(result.rowcount)

    async def mark_running(self, session: AsyncSession, run_id: UUID) -> None:
        await session.execute(
            update(AgentRunRow).where(AgentRunRow.id == run_id).values(status="RUNNING")
        )
        await session.commit()

    async def update_route(
        self,
        session: AsyncSession,
        run_id: UUID,
        *,
        intent: str,
        profile_name: str,
        profile_version: str,
        conservative: bool,
    ) -> None:
        await session.execute(
            update(AgentRunRow)
            .where(AgentRunRow.id == run_id)
            .values(
                intent=intent,
                profile_name=profile_name,
                profile_version=profile_version,
                conservative_mode=conservative,
            )
        )
        await session.commit()

    async def complete(
        self,
        session: AsyncSession,
        run: AgentRunRow,
        answer: str,
        citations: list[dict[str, Any]],
        latency_ms: int,
        tool_call_count: int,
    ) -> MessageRow:
        message = MessageRow(
            conversation_id=run.conversation_id,
            role="ASSISTANT",
            content=answer,
            citations=citations,
            request_id=run.request_id,
            trace_id=run.trace_id,
        )
        session.add(message)
        await session.flush()
        await session.execute(
            update(AgentRunRow)
            .where(AgentRunRow.id == run.id)
            .values(
                status="SUCCEEDED",
                assistant_message_id=message.id,
                latency_ms=latency_ms,
                tool_call_count=tool_call_count,
                finished_at=datetime.now(UTC),
            )
        )
        await session.commit()
        await session.refresh(message)
        return message

    async def fail(self, session: AsyncSession, run_id: UUID, error: str, latency_ms: int) -> None:
        await session.execute(
            update(AgentRunRow)
            .where(AgentRunRow.id == run_id)
            .values(
                status="FAILED",
                error_message=error[:1000],
                latency_ms=latency_ms,
                finished_at=datetime.now(UTC),
            )
        )
        await session.commit()

    async def append_event(
        self,
        session: AsyncSession,
        run_id: UUID,
        conversation_id: UUID,
        event_type: str,
        data: dict[str, Any],
    ) -> RunEventRow:
        sequence = await session.scalar(
            select(func.coalesce(func.max(RunEventRow.sequence), 0)).where(
                RunEventRow.run_id == run_id
            )
        )
        row = RunEventRow(
            run_id=run_id,
            conversation_id=conversation_id,
            sequence=int(sequence or 0) + 1,
            event_type=event_type,
            event_data=data,
        )
        session.add(row)
        await session.commit()
        await session.refresh(row)
        return row

    async def events_after(
        self, session: AsyncSession, conversation_id: UUID, event_id: int
    ) -> list[tuple[RunEventRow, AgentRunRow]]:
        rows = await session.execute(
            select(RunEventRow, AgentRunRow)
            .join(AgentRunRow, AgentRunRow.id == RunEventRow.run_id)
            .where(
                RunEventRow.conversation_id == conversation_id,
                RunEventRow.id > event_id,
            )
            .order_by(RunEventRow.id)
        )
        return list(rows.tuples())

    async def timeline(
        self, session: AsyncSession, run_id: UUID, user_id: int
    ) -> dict[str, Any] | None:
        run = await session.scalar(
            select(AgentRunRow).where(AgentRunRow.id == run_id, AgentRunRow.user_id == user_id)
        )
        if run is None:
            return None
        events = list(
            await session.scalars(
                select(RunEventRow)
                .where(RunEventRow.run_id == run_id)
                .order_by(RunEventRow.sequence)
            )
        )
        return {
            "runId": str(run.id),
            "status": run.status,
            "intent": run.intent,
            "profileName": run.profile_name,
            "profileVersion": run.profile_version,
            "conservativeMode": run.conservative_mode,
            "traceId": run.trace_id,
            "events": [
                {
                    "type": event.event_type,
                    "sequence": event.sequence,
                    "timestamp": event.created_at.isoformat(),
                    "data": event.event_data,
                }
                for event in events
            ],
        }

    async def retrieval_trace(
        self, session: AsyncSession, run_id: UUID, user_id: int
    ) -> list[dict[str, Any]] | None:
        run = await session.scalar(
            select(AgentRunRow).where(AgentRunRow.id == run_id, AgentRunRow.user_id == user_id)
        )
        if run is None:
            return None
        rows = list(
            await session.scalars(
                select(RetrievalTraceRow)
                .where(RetrievalTraceRow.run_id == run_id)
                .order_by(RetrievalTraceRow.created_at)
            )
        )
        return [
            {
                "id": str(row.id),
                "query": row.query,
                "hitCount": row.hit_count,
                "reranked": row.reranked,
                "candidates": row.candidates,
                "createdAt": row.created_at.isoformat(),
            }
            for row in rows
        ]

    async def save_retrieval_trace(
        self,
        session: AsyncSession,
        run_id: UUID,
        query: str,
        candidates: list[dict[str, Any]],
        reranked: bool,
    ) -> None:
        session.add(
            RetrievalTraceRow(
                run_id=run_id,
                query=query,
                hit_count=len(candidates),
                reranked=reranked,
                candidates=candidates,
            )
        )
        await session.commit()

    async def save_tool_call(
        self,
        session: AsyncSession,
        run_id: UUID,
        tool_name: str,
        request_json: dict[str, Any],
        response_json: dict[str, Any],
        success: bool,
        latency_ms: int,
    ) -> None:
        session.add(
            ToolCallRow(
                run_id=run_id,
                tool_name=tool_name,
                request_json=sanitize_audit_payload(request_json),
                response_json=sanitize_audit_payload(response_json),
                sensitivity_level="INTERNAL",
                redaction_version="audit-v1",
                success=success,
                latency_ms=latency_ms,
            )
        )
        await session.commit()

    async def tool_calls(
        self, session: AsyncSession, user_id: int, limit: int
    ) -> list[dict[str, Any]]:
        rows = await session.execute(
            select(ToolCallRow, AgentRunRow)
            .join(AgentRunRow, AgentRunRow.id == ToolCallRow.run_id)
            .where(AgentRunRow.user_id == user_id)
            .order_by(ToolCallRow.created_at.desc())
            .limit(limit)
        )
        return [
            {
                "id": str(call.id),
                "runId": str(call.run_id),
                "toolName": call.tool_name,
                "success": call.success,
                "latencyMillis": call.latency_ms,
                "createdAt": call.created_at.isoformat(),
            }
            for call, _run in rows
        ]

    async def purge_conversation(self, session: AsyncSession, conversation_id: UUID) -> None:
        await session.execute(delete(ConversationRow).where(ConversationRow.id == conversation_id))
        await session.commit()
