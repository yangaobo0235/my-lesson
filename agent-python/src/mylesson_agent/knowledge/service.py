import hashlib
from datetime import UTC, datetime
from typing import Any

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.infrastructure.orm import KnowledgeChunkRow, KnowledgeSourceRow
from mylesson_agent.llm.client import ModelClient


class KnowledgeIndexer:
    def __init__(self, model: ModelClient, chunk_size: int = 800, overlap: int = 100) -> None:
        self._model = model
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
        if existing and existing.content_hash == content_hash:
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
        session.add(source)
        await session.flush()
        await session.execute(
            delete(KnowledgeChunkRow).where(KnowledgeChunkRow.source_id == source.id)
        )
        chunks = self._chunks(content)
        embeddings = await self._model.embed(chunks)
        for index, (chunk, embedding) in enumerate(zip(chunks, embeddings, strict=True)):
            session.add(
                KnowledgeChunkRow(
                    source_id=source.id,
                    chunk_index=index,
                    title=title,
                    content=chunk,
                    metadata_json=metadata,
                    embedding=embedding,
                )
            )
        source.status = "ACTIVE"
        source.indexed_at = datetime.now(UTC)
        await session.commit()
        await session.refresh(source)
        return source

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
