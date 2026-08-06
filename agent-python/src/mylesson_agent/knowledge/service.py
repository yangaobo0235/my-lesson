import hashlib
from datetime import UTC, datetime
from typing import Any
from uuid import UUID, uuid5

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.infrastructure.orm import KnowledgeChunkRow, KnowledgeSourceRow
from mylesson_agent.llm.client import ModelClient
from mylesson_agent.rag.search_client import JavaKnowledgeSearchClient


class KnowledgeIndexer:
    def __init__(
        self,
        model: ModelClient,
        keyword_search: JavaKnowledgeSearchClient,
        chunk_size: int = 800,
        overlap: int = 100,
    ) -> None:
        self._model = model
        self._keyword_search = keyword_search
        self._chunk_size = chunk_size
        self._overlap = overlap

    async def upsert(
        self,
        session: AsyncSession,
        *,
        source_type: str,
        source_id: str,
        title: str,
        source_url: str,
        content: str,
        content_version: int,
        metadata: dict[str, Any],
        event_id: UUID | None = None,
    ) -> KnowledgeSourceRow:
        existing = await session.scalar(
            select(KnowledgeSourceRow).where(
                KnowledgeSourceRow.source_type == source_type,
                KnowledgeSourceRow.source_id == source_id,
            )
        )
        if existing and existing.content_version > content_version:
            return existing
        content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
        content_changed = existing is None or existing.content_hash != content_hash
        index_current = bool(
            existing
            and existing.content_hash == content_hash
            and existing.content_version >= content_version
            and (
                not self._keyword_search.enabled
                or existing.es_indexed_version >= content_version
            )
            and existing.status == "ACTIVE"
        )
        if index_current and existing is not None:
            return existing
        source = existing or KnowledgeSourceRow(
            source_type=source_type,
            source_id=source_id,
            title=title,
            source_url=source_url,
            content_hash=content_hash,
            content_version=content_version,
        )
        source.title = title
        source.source_url = source_url
        source.content_hash = content_hash
        source.content_version = content_version
        source.status = "INDEXING"
        source.error_message = None
        source.es_index_error = None
        session.add(source)
        await session.flush()

        if content_changed:
            await session.execute(
                delete(KnowledgeChunkRow).where(KnowledgeChunkRow.source_id == source.id)
            )
            chunks = self._chunks(content)
            embeddings = await self._model.embed(chunks)
            for index, (chunk, embedding) in enumerate(zip(chunks, embeddings, strict=True)):
                session.add(
                    KnowledgeChunkRow(
                        id=uuid5(source.id, f"chunk:{index}"),
                        source_id=source.id,
                        chunk_index=index,
                        title=title,
                        content=chunk,
                        metadata_json=metadata,
                        embedding=embedding,
                    )
                )
            await session.flush()

        rows = list(
            await session.scalars(
                select(KnowledgeChunkRow)
                .where(KnowledgeChunkRow.source_id == source.id)
                .order_by(KnowledgeChunkRow.chunk_index)
            )
        )
        if not rows:
            raise ValueError("Knowledge source produced no chunks")
        try:
            result = await self._keyword_search.upsert(
                event_id=event_id,
                source_type=source_type,
                source_id=source_id,
                content_version=content_version,
                chunks=[
                    {
                        "chunkId": str(row.id),
                        "chunkIndex": row.chunk_index,
                        "title": row.title,
                        "content": row.content,
                        "sourceUrl": source_url,
                        "contentHash": hashlib.sha256(row.content.encode("utf-8")).hexdigest(),
                    }
                    for row in rows
                ],
            )
            if result.get("status") == "SKIPPED_OLD_VERSION":
                raise RuntimeError("Elasticsearch contains a newer source version")
        except Exception as exception:
            source.es_index_error = f"{exception.__class__.__name__}: {exception}"[:1000]
            raise

        now = datetime.now(UTC)
        if self._keyword_search.enabled:
            source.es_indexed_version = content_version
            source.es_indexed_at = now
        source.status = "ACTIVE"
        source.indexed_at = now
        await session.flush()
        return source

    async def delete(
        self,
        session: AsyncSession,
        source: KnowledgeSourceRow,
        *,
        content_version: int,
    ) -> None:
        source.status = "DELETING"
        source.es_index_error = None
        await session.flush()
        try:
            result = await self._keyword_search.delete(
                source_type=source.source_type,
                source_id=source.source_id,
                content_version=content_version,
            )
            if result.get("status") == "SKIPPED_OLD_VERSION":
                raise RuntimeError("Elasticsearch contains a newer source version")
        except Exception as exception:
            source.es_index_error = f"{exception.__class__.__name__}: {exception}"[:1000]
            raise
        await session.execute(
            delete(KnowledgeChunkRow).where(KnowledgeChunkRow.source_id == source.id)
        )
        now = datetime.now(UTC)
        source.status = "DELETED"
        source.content_version = content_version
        if self._keyword_search.enabled:
            source.es_indexed_version = content_version
            source.es_indexed_at = now
        source.indexed_at = now
        await session.flush()

    def _chunks(self, content: str) -> list[str]:
        normalized = "\n".join(line.strip() for line in content.splitlines() if line.strip())
        if not normalized:
            return ["空内容"]
        chunks: list[str] = []
        offset = 0
        while offset < len(normalized):
            end = min(len(normalized), offset + self._chunk_size)
            chunks.append(normalized[offset:end])
            if end == len(normalized):
                break
            offset = max(offset + 1, end - self._overlap)
        return chunks
