import asyncio
import json
from collections.abc import AsyncIterator
from datetime import UTC, datetime
from typing import Any
from uuid import UUID

from redis.asyncio import Redis
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.conversation.repository import ConversationRepository
from mylesson_agent.domain.api_models import StreamEvent
from mylesson_agent.infrastructure.database import Database
from mylesson_agent.infrastructure.orm import AgentRunRow, RunEventRow
from mylesson_agent.observability.metrics import SSE_CONNECTIONS


class EventPublisher:
    def __init__(
        self, redis: Redis, repository: ConversationRepository, database: Database
    ) -> None:
        self._redis = redis
        self._repository = repository
        self._database = database

    async def publish(
        self,
        session: AsyncSession,
        *,
        event_type: str,
        conversation_id: UUID,
        run_id: UUID,
        request_id: UUID,
        trace_id: str,
        data: dict[str, Any],
    ) -> StreamEvent:
        row = await self._repository.append_event(
            session, run_id, conversation_id, event_type, data
        )
        event = StreamEvent(
            event_id=row.id,
            sequence=row.sequence,
            type=event_type,
            conversation_id=conversation_id,
            run_id=run_id,
            request_id=request_id,
            trace_id=trace_id,
            timestamp=datetime.now(UTC),
            data=data,
        )
        payload = event.model_dump_json(by_alias=True)
        await self._redis.publish(self._channel(conversation_id), payload)
        return event

    async def stream(self, conversation_id: UUID, after_event_id: int = 0) -> AsyncIterator[str]:
        pubsub = self._redis.pubsub()
        await pubsub.subscribe(self._channel(conversation_id))
        SSE_CONNECTIONS.inc()
        cursor = after_event_id
        try:
            async with self._database.sessions() as session:
                persisted = await self._repository.events_after(
                    session, conversation_id, cursor
                )
            for event_row, run in persisted:
                cursor = event_row.id
                yield self._format(self._from_row(event_row, run))
            while True:
                try:
                    message = await asyncio.wait_for(
                        pubsub.get_message(ignore_subscribe_messages=True, timeout=1.0),
                        timeout=15.0,
                    )
                except TimeoutError:
                    yield ": keepalive\n\n"
                    continue
                if message is None:
                    await asyncio.sleep(0.05)
                    continue
                data = message["data"]
                text = data.decode("utf-8") if isinstance(data, bytes) else str(data)
                parsed = json.loads(text)
                if int(parsed["eventId"]) <= cursor:
                    continue
                cursor = int(parsed["eventId"])
                yield self._format_json(parsed, text)
        finally:
            SSE_CONNECTIONS.dec()
            await pubsub.unsubscribe(self._channel(conversation_id))
            await pubsub.aclose()  # type: ignore[no-untyped-call]

    @staticmethod
    def _channel(conversation_id: UUID) -> str:
        return f"ml:agent:sse:{conversation_id}"

    @staticmethod
    def _from_row(row: RunEventRow, run: AgentRunRow) -> StreamEvent:
        return StreamEvent(
            event_id=row.id,
            sequence=row.sequence,
            type=row.event_type,
            conversation_id=row.conversation_id,
            run_id=row.run_id,
            request_id=run.request_id,
            trace_id=run.trace_id,
            timestamp=row.created_at,
            data=row.event_data,
        )

    @classmethod
    def _format(cls, event: StreamEvent) -> str:
        payload = event.model_dump_json(by_alias=True)
        return cls._format_json(event.model_dump(by_alias=True), payload)

    @staticmethod
    def _format_json(event: dict[str, Any], payload: str) -> str:
        return f"id: {event['eventId']}\nevent: {event['type']}\ndata: {payload}\n\n"
