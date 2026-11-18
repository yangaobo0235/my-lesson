"""ES与Qdrant稳定ID副本、逐路可见性复核及授权混合召回。作者：yangaobo。"""

import asyncio
import hashlib
from collections.abc import Awaitable, Callable
from uuid import UUID

import httpx

from servicedesk_ai.bootstrap.components import ModelSettings
from servicedesk_ai.infrastructure.models import EmbeddingResponse
from servicedesk_ai.schemas.indexing import ParsedChunk


async def replica_request(
    client: httpx.AsyncClient,
    method: str,
    path: str,
    *,
    json: object | None = None,
    params: dict[str, str] | None = None,
) -> httpx.Response:
    """仅供本适配的确定性读写；未知响应沿原ID及原内容有限重放，不重做Embedding。"""
    for attempt in range(3):
        try:
            response = await client.request(method, path, json=json, params=params)
            if response.status_code not in {429, 502, 503, 504} or attempt == 2:
                return response
        except httpx.TransportError:
            if attempt == 2:
                raise
        await asyncio.sleep(0.2 * (attempt + 1))
    raise RuntimeError("副本请求重试状态异常")


class IndexReplicas:
    """MySQL是正文事实；独立副本均成功核对后，调用者才能提交SUCCEEDED。"""

    def __init__(
        self,
        es: httpx.AsyncClient,
        qdrant: httpx.AsyncClient,
        index: str,
        collection: str,
        settings: ModelSettings,
    ) -> None:
        """连接归进程资源所有者关闭，本适配不自行创建额外池。"""
        self.es, self.qdrant = es, qdrant
        self.index, self.collection, self.settings = index, collection, settings

    async def prepare(self) -> None:
        """创建或核对当前空间；只补缺少的映射，已有不兼容类型拒绝写入。"""
        fields = {
            "chunk_id": {"type": "keyword"},
            "index_job_id": {"type": "keyword"},
            "document_id": {"type": "keyword"},
            "vector_space": {"type": "keyword"},
            "content_hash": {"type": "keyword"},
            "content": {"type": "text", "analyzer": "smartcn"},
            "error_codes": {"type": "keyword"},
        }
        response = await replica_request(self.es, "GET", f"/{self.index}/_mapping")
        if response.status_code == 404:
            try:
                created = await replica_request(
                    self.es,
                    "PUT",
                    f"/{self.index}",
                    json={"mappings": {"dynamic": "strict", "properties": fields}},
                )
                if not (
                    created.status_code == 400
                    and created.json().get("error", {}).get("type")
                    == "resource_already_exists_exception"
                ):
                    created.raise_for_status()
            except httpx.TransportError:
                # 创建结果未知时只读原名称；后续完整映射核对决定能否使用。
                pass
            response = await replica_request(self.es, "GET", f"/{self.index}/_mapping")
        response.raise_for_status()
        mapping = response.json()[self.index]["mappings"]
        existing = mapping.get("properties", {})
        if mapping.get("dynamic") != "strict" or any(
            name in existing
            and any(existing[name].get(key) != value for key, value in definition.items())
            for name, definition in fields.items()
        ):
            raise ValueError("关键词字段类型或分词器不匹配，须显式重建索引")
        missing = {name: definition for name, definition in fields.items() if name not in existing}
        if missing:
            updated = await replica_request(
                self.es, "PUT", f"/{self.index}/_mapping", json={"properties": missing}
            )
            updated.raise_for_status()
        response = await replica_request(self.qdrant, "GET", f"/collections/{self.collection}")
        if response.status_code == 404:
            try:
                created = await replica_request(
                    self.qdrant,
                    "PUT",
                    f"/collections/{self.collection}",
                    json={"vectors": {"size": 1024, "distance": "Cosine"}},
                )
                if created.status_code != 409:
                    created.raise_for_status()
            except httpx.TransportError:
                pass
            response = await replica_request(self.qdrant, "GET", f"/collections/{self.collection}")
        response.raise_for_status()
        vectors = response.json()["result"]["config"]["params"]["vectors"]
        if vectors.get("size") != 1024 or vectors.get("distance") != "Cosine":
            raise ValueError("向量空间不匹配")
        payload_schema = response.json()["result"].get("payload_schema", {})
        for field in ("index_job_id", "document_id", "vector_space"):
            if field in payload_schema:
                if payload_schema[field].get("data_type") != "keyword":
                    raise ValueError("向量授权过滤字段类型不匹配")
                continue
            response = await replica_request(
                self.qdrant,
                "PUT",
                f"/collections/{self.collection}/index",
                params={"wait": "true"},
                json={"field_name": field, "field_schema": "keyword"},
            )
            response.raise_for_status()

    def payload(self, chunk: ParsedChunk, job_id: str, document_id: str) -> dict[str, object]:
        """摘要核对副本完整内容，向量点与片段身份无损对应。"""
        return {
            "chunk_id": chunk.id,
            "index_job_id": job_id,
            "document_id": document_id,
            "vector_space": self.settings.vector_space,
            "content_hash": hashlib.sha256(chunk.content.encode()).hexdigest(),
        }

    async def current(self, chunks: list[ParsedChunk]) -> tuple[dict, dict]:
        """精确读取稳定ID，恢复只补缺失或不匹配的副本。"""
        response = await replica_request(
            self.es, "POST", f"/{self.index}/_mget", json={"ids": [c.id for c in chunks]}
        )
        response.raise_for_status()
        es = {d["_id"]: d["_source"] for d in response.json()["docs"] if d.get("found")}
        response = await replica_request(
            self.qdrant,
            "POST",
            f"/collections/{self.collection}/points",
            json={
                "ids": [str(UUID(c.id)) for c in chunks],
                "with_payload": True,
                "with_vector": False,
            },
        )
        response.raise_for_status()
        qdrant = {UUID(p["id"]).hex: p["payload"] for p in response.json()["result"]}
        return es, qdrant

    async def write_and_verify(
        self,
        job_id: str,
        document_id: str,
        chunks: list[ParsedChunk],
        embed: Callable[[list[str]], Awaitable[EmbeddingResponse]],
    ) -> None:
        """网络及Embedding均在MySQL事务外，完成后逐路复核完整清单。"""
        chunks = [chunk for chunk in chunks if chunk.node_kind == "LEAF"]
        for offset in range(0, len(chunks), 10):
            batch = chunks[offset : offset + 10]
            es, qdrant = await self.current(batch)
            missing = [c for c in batch if qdrant.get(c.id) != self.payload(c, job_id, document_id)]
            if missing:
                embeddings = await embed([c.content for c in missing])
                response = await replica_request(
                    self.qdrant,
                    "PUT",
                    f"/collections/{self.collection}/points",
                    params={"wait": "true"},
                    json={
                        "points": [
                            {
                                "id": str(UUID(c.id)),
                                "vector": vector,
                                "payload": self.payload(c, job_id, document_id),
                            }
                            for c, vector in zip(missing, embeddings.vectors, strict=True)
                        ]
                    },
                )
                response.raise_for_status()
                if response.json().get("result", {}).get("status") != "completed":
                    raise ValueError("向量写入未完成")
            for chunk in batch:
                payload = self.payload(chunk, job_id, document_id) | {
                    "content": chunk.content,
                    "error_codes": chunk.error_codes,
                }
                if es.get(chunk.id) != payload:
                    response = await replica_request(
                        self.es,
                        "PUT",
                        f"/{self.index}/_doc/{chunk.id}",
                        params={"refresh": "wait_for"},
                        json=payload,
                    )
                    response.raise_for_status()
            actual_es, actual_q = await self.current(batch)
            for chunk in batch:
                payload = self.payload(chunk, job_id, document_id)
                if actual_q.get(chunk.id) != payload or actual_es.get(chunk.id) != (
                    payload | {"content": chunk.content, "error_codes": chunk.error_codes}
                ):
                    raise ValueError("双索引内容不一致")
        # 搜索接口而非仅点查也须可见；禁止单边成功被发布。
        response = await replica_request(
            self.es,
            "POST",
            f"/{self.index}/_search",
            json={"size": 0, "track_total_hits": True, "query": {"term": {"index_job_id": job_id}}},
        )
        response.raise_for_status()
        if response.json()["hits"]["total"]["value"] != len(chunks):
            raise ValueError("关键词完整清单不可见")
        response = await replica_request(
            self.qdrant,
            "POST",
            f"/collections/{self.collection}/points/count",
            json={
                "exact": True,
                "filter": {"must": [{"key": "index_job_id", "match": {"value": job_id}}]},
            },
        )
        response.raise_for_status()
        if response.json()["result"]["count"] != len(chunks):
            raise ValueError("向量完整清单不可见")

    async def search(
        self,
        query: str,
        authorized_jobs: list[str],
        limit: int = 8,
        *,
        vector: list[float],
        candidates_per_source: int = 50,
        rrf_k: int = 60,
        evidence_limit: int = 8,
    ) -> list[str]:
        """两路查询前授权过滤，RRF只负责排序；一路失败让整个调用失败。"""
        if not authorized_jobs:
            return []
        if (
            (candidates_per_source, rrf_k, evidence_limit) != (50, 60, 8)
            or not 1 <= limit <= evidence_limit
            or len(authorized_jobs) > 500
        ):
            raise ValueError("检索预算超限")
        response = await replica_request(
            self.es,
            "POST",
            f"/{self.index}/_search",
            json={
                "size": candidates_per_source,
                "query": {
                    "bool": {
                        "must": [{"match": {"content": query}}],
                        "filter": [
                            {"terms": {"index_job_id": authorized_jobs}},
                            {"term": {"vector_space": self.settings.vector_space}},
                        ],
                    }
                },
            },
        )
        response.raise_for_status()
        keyword = [hit["_id"] for hit in response.json()["hits"]["hits"]]
        response = await replica_request(
            self.qdrant,
            "POST",
            f"/collections/{self.collection}/points/query",
            json={
                "query": vector,
                "limit": candidates_per_source,
                "with_payload": True,
                "params": {"exact": True},
                "filter": {
                    "must": [
                        {"key": "index_job_id", "match": {"any": authorized_jobs}},
                        {"key": "vector_space", "match": {"value": self.settings.vector_space}},
                    ]
                },
            },
        )
        response.raise_for_status()
        semantic = [UUID(point["id"]).hex for point in response.json()["result"]["points"]]
        scores: dict[str, float] = {}
        for results in (keyword, semantic):
            for rank, chunk_id in enumerate(results, 1):
                scores[chunk_id] = scores.get(chunk_id, 0) + 1 / (rrf_k + rank)
        return sorted(scores, key=lambda cid: (-scores[cid], cid))[:limit]

    async def verify(self, job_id: str, document_id: str, chunks: list[ParsedChunk]) -> bool:
        """发布前只读复核当前副本，不用历史成功标志掩盖副本丢失。"""
        chunks = [chunk for chunk in chunks if chunk.node_kind == "LEAF"]
        if not chunks or len(chunks) > 500:
            return False
        es, qdrant = await self.current(chunks)
        for chunk in chunks:
            payload = self.payload(chunk, job_id, document_id)
            if qdrant.get(chunk.id) != payload or es.get(chunk.id) != (
                payload | {"content": chunk.content, "error_codes": chunk.error_codes}
            ):
                return False
        response = await replica_request(
            self.es,
            "POST",
            f"/{self.index}/_count",
            json={"query": {"term": {"index_job_id": job_id}}},
        )
        response.raise_for_status()
        if response.json()["count"] != len(chunks):
            return False
        response = await replica_request(
            self.qdrant,
            "POST",
            f"/collections/{self.collection}/points/count",
            json={
                "exact": True,
                "filter": {"must": [{"key": "index_job_id", "match": {"value": job_id}}]},
            },
        )
        response.raise_for_status()
        return response.json()["result"]["count"] == len(chunks)
