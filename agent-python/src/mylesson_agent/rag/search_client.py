from dataclasses import dataclass
from time import monotonic
from typing import Any
from uuid import UUID

import httpx

from mylesson_agent.config import Settings
from mylesson_agent.observability.metrics import SEARCH_BACKEND_CALLS, SEARCH_BACKEND_LATENCY


@dataclass(frozen=True)
class KeywordSearchHit:
    chunk_id: str
    rank: int
    score: float
    source_type: str
    source_id: str
    content_version: int


class JavaKnowledgeSearchClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client = httpx.AsyncClient(timeout=settings.search_timeout_seconds)

    async def close(self) -> None:
        await self._client.aclose()

    @property
    def enabled(self) -> bool:
        return self._settings.keyword_backend_enabled

    async def search(
        self,
        query: str,
        top_k: int,
        source_types: list[str] | None = None,
    ) -> list[KeywordSearchHit]:
        if not self._settings.keyword_backend_enabled:
            return []
        payload = await self._request(
            "POST",
            "/internal/ai/knowledge/search/keyword",
            operation="search",
            json={"query": query, "topK": top_k, "sourceTypes": source_types or []},
        )
        hits = payload.get("hits") if isinstance(payload, dict) else None
        if not isinstance(hits, list):
            raise ValueError("Java keyword search response is missing hits")
        return [
            KeywordSearchHit(
                chunk_id=str(item["chunkId"]),
                rank=int(item.get("rank") or index),
                score=float(item.get("score") or 0.0),
                source_type=str(item["sourceType"]),
                source_id=str(item["sourceId"]),
                content_version=int(item["contentVersion"]),
            )
            for index, item in enumerate(hits, start=1)
            if isinstance(item, dict)
        ]

    async def upsert(
        self,
        *,
        event_id: UUID | None,
        source_type: str,
        source_id: str,
        content_version: int,
        chunks: list[dict[str, Any]],
    ) -> dict[str, Any]:
        if not self._settings.keyword_backend_enabled:
            return {"status": "DISABLED", "contentVersion": content_version}
        result = await self._request(
            "PUT",
            "/internal/ai/knowledge/chunks",
            operation="upsert",
            json={
                "eventId": str(event_id) if event_id else None,
                "sourceType": source_type,
                "sourceId": source_id,
                "contentVersion": content_version,
                "chunks": chunks,
            },
        )
        if not isinstance(result, dict):
            raise ValueError("Java knowledge upsert response must be an object")
        return result

    async def delete(
        self,
        *,
        source_type: str,
        source_id: str,
        content_version: int,
    ) -> dict[str, Any]:
        if not self._settings.keyword_backend_enabled:
            return {"status": "DISABLED", "contentVersion": content_version}
        result = await self._request(
            "DELETE",
            f"/internal/ai/knowledge/sources/{source_type}/{source_id}",
            operation="delete",
            params={"contentVersion": content_version},
        )
        if not isinstance(result, dict):
            raise ValueError("Java knowledge delete response must be an object")
        return result

    async def _request(
        self,
        method: str,
        path: str,
        *,
        operation: str,
        json: dict[str, Any] | None = None,
        params: dict[str, Any] | None = None,
    ) -> Any:
        headers: dict[str, str] = {}
        if self._settings.service_token:
            headers["X-Internal-Token"] = self._settings.service_token.get_secret_value()
        started = monotonic()
        try:
            response = await self._client.request(
                method,
                f"{self._settings.search_service_url.rstrip('/')}{path}",
                json=json,
                params=params,
                headers=headers,
            )
            response.raise_for_status()
            payload = response.json()
            if isinstance(payload, dict) and "code" in payload:
                if int(payload.get("code") or 0) != 1000:
                    raise RuntimeError(str(payload.get("coderMessage") or payload.get("message")))
                payload = payload.get("data")
            SEARCH_BACKEND_CALLS.labels("elasticsearch", operation, "success").inc()
            return payload
        except Exception:
            SEARCH_BACKEND_CALLS.labels("elasticsearch", operation, "failed").inc()
            raise
        finally:
            SEARCH_BACKEND_LATENCY.labels("elasticsearch", operation).observe(
                monotonic() - started
            )
