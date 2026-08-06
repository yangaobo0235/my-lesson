import httpx
import respx
from pydantic import SecretStr

from mylesson_agent.config import Settings
from mylesson_agent.rag.search_client import JavaKnowledgeSearchClient


@respx.mock
async def test_java_keyword_search_unwraps_standard_java_response() -> None:
    settings = Settings(
        search_service_url="http://search.test",
        service_token=SecretStr("service-token"),
    )
    route = respx.post("http://search.test/internal/ai/knowledge/search/keyword").mock(
        return_value=httpx.Response(
            200,
            json={
                "code": 1000,
                "message": "请求成功",
                "data": {
                    "hits": [
                        {
                            "chunkId": "00000000-0000-0000-0000-000000000001",
                            "rank": 1,
                            "score": 8.4,
                            "sourceType": "COURSE",
                            "sourceId": "1",
                            "contentVersion": 2,
                        }
                    ]
                },
            },
        )
    )
    client = JavaKnowledgeSearchClient(settings)
    try:
        hits = await client.search("Java", 20)
    finally:
        await client.close()

    assert hits[0].source_type == "COURSE"
    assert hits[0].content_version == 2
    assert route.calls.last.request.headers["X-Internal-Token"] == "service-token"


@respx.mock
async def test_java_search_treats_java_error_envelope_as_failure() -> None:
    settings = Settings(search_service_url="http://search.test")
    respx.post("http://search.test/internal/ai/knowledge/search/keyword").mock(
        return_value=httpx.Response(
            200,
            json={"code": 2000, "message": "服务器异常", "coderMessage": "ES unavailable"},
        )
    )
    client = JavaKnowledgeSearchClient(settings)
    try:
        try:
            await client.search("Java", 20)
        except RuntimeError as exception:
            assert "ES unavailable" in str(exception)
        else:
            raise AssertionError("Expected Java search failure")
    finally:
        await client.close()
