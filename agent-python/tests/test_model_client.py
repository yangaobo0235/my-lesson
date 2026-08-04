import json

import httpx
import respx
from pydantic import SecretStr

from mylesson_agent.config import Settings
from mylesson_agent.llm.client import ModelClient


@respx.mock
async def test_rerank_uses_dashscope_native_endpoint() -> None:
    settings = Settings(
        dashscope_api_key=SecretStr("sk-test"),
        rerank_url="https://dashscope.test/api/v1/rerank",
        rerank_model="qwen3-rerank",
    )
    route = respx.post(settings.rerank_url).mock(
        return_value=httpx.Response(
            200,
            json={
                "output": {
                    "results": [
                        {"index": 1, "relevance_score": 0.91},
                        {"index": 0, "relevance_score": 0.72},
                    ]
                }
            },
        )
    )
    client = ModelClient(settings)
    try:
        result = await client.rerank("Java", ["doc-a", "doc-b"], 2)
    finally:
        await client.close()

    assert result == [(1, 0.91), (0, 0.72)]
    assert route.calls.last.request.headers["Authorization"] == "Bearer sk-test"
    body = json.loads(route.calls.last.request.content)
    assert body == {
        "model": "qwen3-rerank",
        "input": {"query": "Java", "documents": ["doc-a", "doc-b"]},
        "parameters": {"top_n": 2, "return_documents": False},
    }
