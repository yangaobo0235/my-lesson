"""既有检索空间的增量映射及拒绝不兼容配置，不破坏存量数据。"""

import json

import httpx
import pytest

from servicedesk_ai.bootstrap.components import ModelSettings
from servicedesk_ai.infrastructure.index_replicas import IndexReplicas, replica_request


@pytest.mark.asyncio
async def test_lost_write_response_reuses_same_object_identity():
    """第一次写入响应丢失后，仅同ID同正文重放，最终以正常响应交付。"""
    calls = []

    def handle(request):
        calls.append((request.method, request.url.path, request.content))
        if len(calls) == 1:
            raise httpx.ReadTimeout("response lost", request=request)
        return httpx.Response(200, json={"result": "updated"})

    async with httpx.AsyncClient(
        transport=httpx.MockTransport(handle), base_url="http://test"
    ) as client:
        response = await replica_request(
            client, "PUT", "/chunks/_doc/fixed", json={"content_hash": "fixed"}
        )
    assert response.status_code == 200
    assert len(calls) == 2 and calls[0] == calls[1]


@pytest.mark.asyncio
@pytest.mark.parametrize("incompatible", [False, True])
async def test_existing_mapping_is_checked_before_additive_upgrade(incompatible):
    """旧空间只补摘要字段，已存在的错误类型必须在任何写入前失败。"""
    writes = []

    def handle(request):
        if request.method == "PUT":
            writes.append((request.url.path, json.loads(request.content)))
            return httpx.Response(200, json={"acknowledged": True})
        if request.url.path == "/chunks/_mapping":
            return httpx.Response(
                200,
                json={
                    "chunks": {
                        "mappings": {
                            "dynamic": "strict",
                            "properties": {
                                "chunk_id": {"type": "keyword"},
                                "index_job_id": {"type": "text" if incompatible else "keyword"},
                                "document_id": {"type": "keyword"},
                                "vector_space": {"type": "keyword"},
                                "content": {"type": "text", "analyzer": "smartcn"},
                                "error_codes": {"type": "keyword"},
                            },
                        }
                    }
                },
            )
        return httpx.Response(
            200,
            json={
                "result": {
                    "config": {"params": {"vectors": {"size": 1024, "distance": "Cosine"}}},
                    "payload_schema": {
                        name: {"data_type": "keyword"}
                        for name in ["index_job_id", "document_id", "vector_space"]
                    },
                }
            },
        )

    async with httpx.AsyncClient(
        transport=httpx.MockTransport(handle), base_url="http://test"
    ) as client:
        replicas = IndexReplicas(client, client, "chunks", "chunks", ModelSettings())
        if incompatible:
            with pytest.raises(ValueError, match="字段类型"):
                await replicas.prepare()
            assert writes == []
        else:
            await replicas.prepare()
            assert writes == [
                ("/chunks/_mapping", {"properties": {"content_hash": {"type": "keyword"}}})
            ]


@pytest.mark.asyncio
@pytest.mark.parametrize("lost_response", [False, True])
@pytest.mark.parametrize("compatible", [False, True])
async def test_creation_race_reads_back_space_before_use(lost_response, compatible):
    """创建响应丢失或另一worker先创建后，必须回读配置，不能把已存在当兼容。"""
    exists = set()
    reads = []

    def handle(request):
        path = request.url.path
        name = "es" if path.startswith("/chunks") else "qdrant"
        if request.method == "GET":
            reads.append(name)
            if name not in exists:
                return httpx.Response(404, json={})
            if name == "es":
                return httpx.Response(
                    200,
                    json={
                        "chunks": {
                            "mappings": {
                                "dynamic": "strict",
                                "properties": {},
                            }
                        }
                    },
                )
            return httpx.Response(
                200,
                json={
                    "result": {
                        "config": {
                            "params": {
                                "vectors": {
                                    "size": 1024 if compatible else 768,
                                    "distance": "Cosine",
                                }
                            }
                        },
                        "payload_schema": {},
                    }
                },
            )
        if path.endswith("/_mapping") or path.endswith("/index"):
            return httpx.Response(200, json={"acknowledged": True})
        exists.add(name)
        if lost_response:
            raise httpx.ReadTimeout("created but response lost", request=request)
        return httpx.Response(
            400 if name == "es" else 409,
            json={"error": {"type": "resource_already_exists_exception"}},
        )

    async with httpx.AsyncClient(
        transport=httpx.MockTransport(handle), base_url="http://test"
    ) as client:
        replicas = IndexReplicas(client, client, "chunks", "chunks", ModelSettings())
        if compatible:
            await replicas.prepare()
        else:
            with pytest.raises(ValueError, match="向量空间"):
                await replicas.prepare()
    assert reads == ["es", "es", "qdrant", "qdrant"]
