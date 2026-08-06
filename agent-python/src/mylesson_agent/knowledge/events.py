from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any
from uuid import UUID

import httpx
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.config import Settings
from mylesson_agent.infrastructure.orm import (
    KnowledgeEventRow,
    KnowledgeSourceRow,
)
from mylesson_agent.knowledge.service import KnowledgeIndexer


@dataclass(frozen=True)
class KnowledgeEvent:
    event_id: UUID
    event_type: str
    aggregate_id: str
    version: int


class KnowledgeEventConsumer:
    def __init__(self, settings: Settings, indexer: KnowledgeIndexer) -> None:
        self._settings = settings
        self._indexer = indexer

    async def consume(self, session: AsyncSession, event: KnowledgeEvent) -> dict[str, Any]:
        source_type = self._source_type(event.event_type)
        existing_event = await session.get(KnowledgeEventRow, event.event_id)
        if existing_event and existing_event.status in {"PROCESSED", "SKIPPED"}:
            return self._result(existing_event)
        row = existing_event or KnowledgeEventRow(
            event_id=event.event_id,
            event_type=event.event_type,
            source_type=source_type,
            source_id=event.aggregate_id,
            content_version=event.version,
            status="RECEIVED",
        )
        row.attempts += 1
        session.add(row)
        await session.commit()

        latest = await session.scalar(
            select(KnowledgeSourceRow).where(
                KnowledgeSourceRow.source_type == source_type,
                KnowledgeSourceRow.source_id == event.aggregate_id,
            )
        )
        latest_event_version = await session.scalar(
            select(func.max(KnowledgeEventRow.content_version)).where(
                KnowledgeEventRow.source_type == source_type,
                KnowledgeEventRow.source_id == event.aggregate_id,
                KnowledgeEventRow.event_id != event.event_id,
                KnowledgeEventRow.status.in_(["PROCESSED", "SKIPPED"]),
            )
        )
        if latest is not None and latest.content_version > event.version:
            row.status = "SKIPPED"
            row.processed_at = datetime.now(UTC)
            await session.commit()
            return self._result(row)

        latest_ready = bool(
            latest
            and latest.content_version >= event.version
            and latest.status in {"ACTIVE", "DELETED"}
            and (
                not self._settings.keyword_backend_enabled
                or latest.es_indexed_version >= event.version
            )
        )
        if latest_ready or int(latest_event_version or 0) >= event.version:
            row.status = "SKIPPED"
            row.processed_at = datetime.now(UTC)
            await session.commit()
            return self._result(row)

        if event.event_type.upper().endswith("_DELETED"):
            try:
                if latest is not None:
                    await self._indexer.delete(
                        session,
                        latest,
                        content_version=event.version,
                    )
                row.status = "PROCESSED"
                row.processed_at = datetime.now(UTC)
                await session.commit()
                return self._result(row)
            except Exception as exception:
                await self._mark_failed(session, event.event_id, exception)
                raise

        try:
            payload = await self._fetch(source_type, event.aggregate_id)
            document = self._document(source_type, payload)
            await self._indexer.upsert(
                session,
                source_type=source_type,
                source_id=event.aggregate_id,
                title=document["title"],
                source_url=document["source_url"],
                content=document["content"],
                content_version=event.version,
                metadata={"eventId": str(event.event_id), "eventType": event.event_type},
                event_id=event.event_id,
            )
            row.status = "PROCESSED"
            row.last_error = None
            row.processed_at = datetime.now(UTC)
            await session.commit()
            return self._result(row)
        except Exception as exception:
            await self._mark_failed(session, event.event_id, exception)
            raise

    @staticmethod
    async def _mark_failed(
        session: AsyncSession,
        event_id: UUID,
        exception: Exception,
    ) -> None:
        await session.rollback()
        failed = await session.get(KnowledgeEventRow, event_id)
        if failed is not None:
            failed.status = "FAILED"
            failed.last_error = f"{exception.__class__.__name__}: {exception}"[:1000]
            await session.commit()

    async def _fetch(self, source_type: str, source_id: str) -> dict[str, Any]:
        routes = {
            "COURSE": f"{self._settings.course_service_url}/internal/ai/courses/{source_id}",
            "ARTICLE": f"{self._settings.sale_service_url}/internal/ai/articles/{source_id}",
            "NOTICE": f"{self._settings.sale_service_url}/internal/ai/notices/{source_id}",
        }
        headers: dict[str, str] = {}
        if self._settings.service_token:
            headers["X-Internal-Token"] = self._settings.service_token.get_secret_value()
        async with httpx.AsyncClient(timeout=self._settings.tool_timeout_seconds) as client:
            response = await client.get(routes[source_type], headers=headers)
            response.raise_for_status()
        payload = response.json()
        data = payload.get("data") if isinstance(payload, dict) and "data" in payload else payload
        if not isinstance(data, dict):
            raise ValueError("Knowledge source response must be an object")
        return data

    @staticmethod
    def _source_type(event_type: str) -> str:
        prefix = event_type.split("_", maxsplit=1)[0].upper()
        if prefix not in {"COURSE", "ARTICLE", "NOTICE"}:
            raise ValueError(f"Unsupported knowledge event type: {event_type}")
        return prefix

    @staticmethod
    def _document(source_type: str, payload: dict[str, Any]) -> dict[str, str]:
        source_id = str(payload.get("id") or "")
        title = str(payload.get("title") or f"{source_type} {source_id}")
        parts = [title]
        for field in ("author", "category", "description", "detail", "content"):
            value = payload.get(field)
            if value:
                parts.append(str(value))
        episodes = payload.get("episodeTitles") or []
        if isinstance(episodes, list):
            parts.extend(str(item) for item in episodes if item)
        return {
            "title": title,
            "source_url": f"mylesson://{source_type.lower()}/{source_id}",
            "content": "\n".join(parts),
        }

    @staticmethod
    def _result(row: KnowledgeEventRow) -> dict[str, Any]:
        return {
            "eventId": str(row.event_id),
            "sourceType": row.source_type,
            "sourceId": row.source_id,
            "version": row.content_version,
            "status": row.status,
            "attempts": row.attempts,
        }
