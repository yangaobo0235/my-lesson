from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from uuid import UUID

from sqlalchemy import Select, select

PROJECT_ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = PROJECT_ROOT / "src"
sys.path.insert(0, str(SOURCE_ROOT))

from mylesson_agent.config import Settings  # noqa: E402
from mylesson_agent.infrastructure.database import Database  # noqa: E402
from mylesson_agent.infrastructure.orm import (  # noqa: E402
    KnowledgeChunkRow,
    KnowledgeSourceRow,
)
from mylesson_agent.rag.search_client import JavaKnowledgeSearchClient  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Backfill active PostgreSQL knowledge chunks into Elasticsearch.",
    )
    parser.add_argument("--batch-size", type=int, default=100)
    parser.add_argument("--limit", type=int)
    parser.add_argument(
        "--source-type",
        action="append",
        dest="source_types",
        help="Only backfill this source type; may be specified more than once.",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Reindex all active sources even when the recorded ES version is current.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Count matching sources and chunks without calling Java or updating PostgreSQL.",
    )
    return parser.parse_args()


def source_query(
    *,
    cursor: UUID | None,
    source_types: list[str],
    force: bool,
    batch_size: int,
) -> Select[tuple[UUID]]:
    query = select(KnowledgeSourceRow.id).where(KnowledgeSourceRow.status == "ACTIVE")
    if not force:
        query = query.where(
            KnowledgeSourceRow.es_indexed_version < KnowledgeSourceRow.content_version
        )
    if source_types:
        query = query.where(KnowledgeSourceRow.source_type.in_(source_types))
    if cursor is not None:
        query = query.where(KnowledgeSourceRow.id > cursor)
    return query.order_by(KnowledgeSourceRow.id).limit(batch_size)


async def backfill(args: argparse.Namespace) -> dict[str, Any]:
    if args.batch_size < 1:
        raise ValueError("--batch-size must be greater than zero")
    if args.limit is not None and args.limit < 1:
        raise ValueError("--limit must be greater than zero")

    settings = Settings()
    database = Database(settings)
    keyword_search = JavaKnowledgeSearchClient(settings)
    source_types = sorted({value.upper() for value in (args.source_types or [])})
    summary: dict[str, Any] = {
        "matchedSources": 0,
        "matchedChunks": 0,
        "indexedSources": 0,
        "failedSources": 0,
        "dryRun": bool(args.dry_run),
        "force": bool(args.force),
        "sourceTypes": source_types,
    }
    cursor: UUID | None = None
    remaining = args.limit
    try:
        if not args.dry_run and not keyword_search.enabled:
            raise RuntimeError("AI_KEYWORD_BACKEND_ENABLED must be true for ES backfill")
        while remaining is None or remaining > 0:
            page_size = args.batch_size if remaining is None else min(args.batch_size, remaining)
            async with database.sessions() as session:
                source_ids = list(
                    await session.scalars(
                        source_query(
                            cursor=cursor,
                            source_types=source_types,
                            force=args.force,
                            batch_size=page_size,
                        )
                    )
                )
            if not source_ids:
                break
            for source_id in source_ids:
                cursor = source_id
                summary["matchedSources"] += 1
                async with database.sessions() as session:
                    source = await session.get(KnowledgeSourceRow, source_id)
                    if source is None:
                        continue
                    chunks = list(
                        await session.scalars(
                            select(KnowledgeChunkRow)
                            .where(KnowledgeChunkRow.source_id == source.id)
                            .order_by(KnowledgeChunkRow.chunk_index)
                        )
                    )
                    summary["matchedChunks"] += len(chunks)
                    if args.dry_run:
                        continue
                    try:
                        if not chunks:
                            raise RuntimeError("Active knowledge source has no chunks")
                        result = await keyword_search.upsert(
                            event_id=None,
                            source_type=source.source_type,
                            source_id=source.source_id,
                            content_version=source.content_version,
                            chunks=[
                                {
                                    "chunkId": str(chunk.id),
                                    "chunkIndex": chunk.chunk_index,
                                    "title": chunk.title,
                                    "content": chunk.content,
                                    "sourceUrl": source.source_url,
                                    "contentHash": hashlib.sha256(
                                        chunk.content.encode("utf-8")
                                    ).hexdigest(),
                                }
                                for chunk in chunks
                            ],
                        )
                        if result.get("status") == "SKIPPED_OLD_VERSION":
                            raise RuntimeError("Elasticsearch contains a newer source version")
                        now = datetime.now(UTC)
                        source.es_indexed_version = source.content_version
                        source.es_indexed_at = now
                        source.es_index_error = None
                        await session.commit()
                        summary["indexedSources"] += 1
                    except Exception as exception:
                        await session.rollback()
                        source = await session.get(KnowledgeSourceRow, source_id)
                        if source is not None:
                            source.es_index_error = (
                                f"{exception.__class__.__name__}: {exception}"[:1000]
                            )
                            await session.commit()
                        summary["failedSources"] += 1
            if remaining is not None:
                remaining -= len(source_ids)
    finally:
        await keyword_search.close()
        await database.close()
    return summary


def main() -> int:
    summary = asyncio.run(backfill(parse_args()))
    print(json.dumps(summary, ensure_ascii=False))
    return 1 if summary["failedSources"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
