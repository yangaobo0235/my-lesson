"""ES与Qdrant稳定ID副本、逐路可见性复核及授权混合召回。作者：yangaobo。"""

import hashlib
from uuid import UUID

import httpx

from servicedesk_ai.bootstrap.components import ModelSettings
from servicedesk_ai.infrastructure.models import ModelClient
from servicedesk_ai.schemas.indexing import ParsedChunk


class IndexReplicas:
    """MySQL是正文事实；独立副本均成功核对后，调用者才能提交SUCCEEDED。"""

    def __init__(
        self,
        es: httpx.AsyncClient,
        qdrant: httpx.AsyncClient,
        model: ModelClient,
        index: str,
        collection: str,
        settings: ModelSettings,
    ) -> None:
        """连接归进程资源所有者关闭，本适配不自行创建额外池。"""
        self.es, self.qdrant, self.model = es, qdrant, model
        self.index, self.collection, self.settings = index, collection, settings

    async def prepare(self) -> None:
        """只建立配置的本环境空间，既有空间维度不符则拒绝写入。"""
        response = await self.es.head(f"/{self.index}")
        if response.status_code == 404:
            created = await self.es.put(
                f"/{self.index}",
                json={
                    "mappings": {
                        "dynamic": "strict",
                        "properties": {
                            "chunk_id": {"type": "keyword"},
                            "index_job_id": {"type": "keyword"},
                            "document_id": {"type": "keyword"},
                            "vector_space": {"type": "keyword"},
                            "content_hash": {"type": "keyword"},
                            "content": {"type": "text", "analyzer": "smartcn"},
                            "error_codes": {"type": "keyword"},
                        },
                    }
                },
            )
            created.raise_for_status()
        else:
            response.raise_for_status()
        response = await self.qdrant.get(f"/collections/{self.collection}")
        if response.status_code == 404:
            response = await self.qdrant.put(
                f"/collections/{self.collection}",
                json={"vectors": {"size": 1024, "distance": "Cosine"}},
            )
            response.raise_for_status()
            for field in ("index_job_id", "document_id", "vector_space"):
                response = await self.qdrant.put(
                    f"/collections/{self.collection}/index",
                    params={"wait": "true"},
                    json={"field_name": field, "field_schema": "keyword"},
                )
                response.raise_for_status()
        else:
            response.raise_for_status()
            vectors = response.json()["result"]["config"]["params"]["vectors"]
            if vectors.get("size") != 1024 or vectors.get("distance") != "Cosine":
                raise ValueError("向量空间不匹配")

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
        response = await self.es.post(f"/{self.index}/_mget", json={"ids": [c.id for c in chunks]})
        response.raise_for_status()
        es = {d["_id"]: d["_source"] for d in response.json()["docs"] if d.get("found")}
        response = await self.qdrant.post(
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
        self, job_id: str, document_id: str, chunks: list[ParsedChunk]
    ) -> None:
        """网络及Embedding均在MySQL事务外，完成后逐路复核完整清单。"""
        for offset in range(0, len(chunks), 10):
            batch = chunks[offset : offset + 10]
            es, qdrant = await self.current(batch)
            missing = [c for c in batch if qdrant.get(c.id) != self.payload(c, job_id, document_id)]
            if missing:
                embeddings = await self.model.embed([c.content for c in missing])
                response = await self.qdrant.put(
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
                    response = await self.es.put(
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
        response = await self.es.post(
            f"/{self.index}/_search",
            json={"size": 0, "track_total_hits": True, "query": {"term": {"index_job_id": job_id}}},
        )
        response.raise_for_status()
        if response.json()["hits"]["total"]["value"] != len(chunks):
            raise ValueError("关键词完整清单不可见")
        response = await self.qdrant.post(
            f"/collections/{self.collection}/points/count",
            json={
                "exact": True,
                "filter": {"must": [{"key": "index_job_id", "match": {"value": job_id}}]},
            },
        )
        response.raise_for_status()
        if response.json()["result"]["count"] != len(chunks):
            raise ValueError("向量完整清单不可见")

    async def search(self, query: str, authorized_jobs: list[str], limit: int = 8) -> list[str]:
        """两路查询前授权过滤，RRF只负责排序；一路失败让整个调用失败。"""
        if not authorized_jobs:
            return []
        if not 1 <= limit <= 8 or len(authorized_jobs) > 500:
            raise ValueError("检索预算超限")
        embedding = await self.model.embed([query])
        response = await self.es.post(
            f"/{self.index}/_search",
            json={
                "size": 50,
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
        response = await self.qdrant.post(
            f"/collections/{self.collection}/points/query",
            json={
                "query": embedding.vectors[0],
                "limit": 50,
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
                scores[chunk_id] = scores.get(chunk_id, 0) + 1 / (60 + rank)
        return sorted(scores, key=lambda cid: (-scores[cid], cid))[:limit]

    async def verify(self, job_id: str, document_id: str, chunks: list[ParsedChunk]) -> bool:
        """发布前只读复核当前副本，不用历史成功标志掩盖副本丢失。"""
        if not chunks or len(chunks) > 500:
            return False
        es, qdrant = await self.current(chunks)
        for chunk in chunks:
            payload = self.payload(chunk, job_id, document_id)
            if qdrant.get(chunk.id) != payload or es.get(chunk.id) != (
                payload | {"content": chunk.content, "error_codes": chunk.error_codes}
            ):
                return False
        response = await self.es.post(
            f"/{self.index}/_count", json={"query": {"term": {"index_job_id": job_id}}}
        )
        response.raise_for_status()
        if response.json()["count"] != len(chunks):
            return False
        response = await self.qdrant.post(
            f"/collections/{self.collection}/points/count",
            json={
                "exact": True,
                "filter": {"must": [{"key": "index_job_id", "match": {"value": job_id}}]},
            },
        )
        response.raise_for_status()
        return response.json()["result"]["count"] == len(chunks)
