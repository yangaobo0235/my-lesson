"""验证真实配置边界和模型协议，不连接共享组件或消耗模型额度。"""

import json
from pathlib import Path

import httpx
import pytest
from pydantic import SecretStr, ValidationError

from servicedesk_ai.bootstrap.components import ExecutionSettings, ModelSettings
from servicedesk_ai.bootstrap.resources import open_resources
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.infrastructure.models import (
    ModelClient,
    ModelMessage,
    ModelProtocolError,
    ModelProviderError,
    ModelRequest,
    ModelTool,
)


def connected_settings(**overrides: object) -> Settings:
    """构造测试专用凭据，连接对象不进行业务请求。"""
    values = {
        "infrastructure_enabled": True,
        "mysql": {"password": "fake-password"},
        "redis": {"password": "fake-password"},
        "elasticsearch": {"password": "fake-password"},
        "qdrant": {"api_key": "fake-key"},
        "model": {"api_key": "fake-key"},
        "rocketmq": {"access_key": "fake-key", "secret_key": "fake-key"},
    }
    return Settings(**(values | overrides))


@pytest.mark.asyncio
@pytest.mark.parametrize("operation", ["chat", "embed"])
@pytest.mark.parametrize(
    "status,code,reason,rejected",
    [
        (400, "Arrearage", "ACCOUNT_UNAVAILABLE", True),
        (401, "invalid_api_key", "AUTHENTICATION_REJECTED", True),
        (429, "rate_limit", "RATE_LIMITED", True),
        (503, "service_error", "SERVICE_UNAVAILABLE", False),
        (408, "timeout", "REQUEST_REJECTED", False),
        (None, None, "TRANSPORT_UNAVAILABLE", False),
    ],
)
async def test_provider_errors_are_safe_distinct_and_never_retried(
    operation, status, code, reason, rejected
):
    """实际SDK错误只保留受控原因，明确拒绝和未知结果区分，单次请求不重试。"""
    requests = []

    def respond(request):
        requests.append(request)
        if status is None:
            raise httpx.ReadError("private-transport-detail", request=request)
        return httpx.Response(
            status,
            json={
                "error": {
                    "code": code,
                    "message": "private-provider-payload",
                    "type": "provider_error",
                }
            },
        )

    client = ModelClient(ModelSettings(api_key=SecretStr("fake-key")), httpx.MockTransport(respond))
    try:
        with pytest.raises(ModelProviderError) as failure:
            if operation == "embed":
                await client.embed(["测试文本"])
            else:
                await client.chat(
                    ModelRequest(messages=(ModelMessage(role="user", content="测试"),))
                )
        assert failure.value.reason == reason
        assert failure.value.rejected is rejected
        assert "private" not in str(failure.value)
        assert len(requests) == 1
    finally:
        await client.close()


def test_missing_credentials_and_cross_environment_fail_closed() -> None:
    """连接模式不接受空密码及串用生产资源，错误不包含密码原值。"""
    with pytest.raises(ValidationError, match="mysql.password"):
        Settings(infrastructure_enabled=True)
    with pytest.raises(ValidationError, match="跨环境") as error:
        connected_settings(mysql={"password": "private-value", "database": "desk_ai_prod"})
    assert "private-value" not in str(error.value)
    with pytest.raises(ValidationError, match="基础环境"):
        connected_settings(environment="prod")
    assert "fake-password" not in repr(connected_settings())


def test_nested_environment_and_invalid_model_contract(monkeypatch: pytest.MonkeyPatch) -> None:
    """嵌套环境变量真实覆盖默认值，空间不接受静默截断或错误参数。"""
    monkeypatch.setenv("DESK_AI_MODEL__API_KEY", "private-value")
    monkeypatch.setenv("DESK_AI_MODEL__DIMENSIONS", "1024")
    assert (
        Settings(infrastructure_enabled=False).model.api_key.get_secret_value() == "private-value"
    )
    assert Settings(infrastructure_enabled=False).model.dimensions == 1024
    assert "private-value" not in str(Settings(infrastructure_enabled=False))
    for values in [{"dimensions": 768}, {"embedding_batch_size": 11}, {"enable_thinking": True}]:
        with pytest.raises(ValidationError):
            ModelSettings(**values)
    with pytest.raises(ValidationError):
        connected_settings(qdrant={"url": "http://user:private-value@host", "api_key": "fake-key"})


def test_execution_defaults_and_model_budget_agree() -> None:
    """配置上限与设计一致，适配器不能使用大于执行预算的单次输出。"""
    budget = ExecutionSettings()
    assert (budget.total_seconds, budget.max_model_calls, budget.max_tool_calls) == (180, 8, 12)
    assert (budget.max_input_tokens, budget.max_output_tokens, budget.total_tokens) == (
        12000,
        2048,
        32000,
    )
    with pytest.raises(ValidationError, match="执行预算"):
        Settings(infrastructure_enabled=False, model={"max_output_tokens": 4096})
    with pytest.raises(ValidationError):
        ExecutionSettings(max_model_calls=9)


@pytest.mark.asyncio
async def test_model_requests_use_documented_parameters() -> None:
    """检查最终HTTP载荷和认证，向量按供应商索引排序，不发起付费调用。"""
    payloads: list[dict[str, object]] = []

    def respond(request: httpx.Request) -> httpx.Response:
        """模拟百炼兼容协议，不替代业务检索与引用校验。"""
        assert request.headers["Authorization"] == "Bearer fake-key"
        payloads.append(json.loads(request.content))
        if request.url.path.endswith("/embeddings"):
            return httpx.Response(
                200,
                json={
                    "object": "list",
                    "model": "text-embedding-v4",
                    "data": [
                        {"object": "embedding", "index": i, "embedding": [float(i + 1)] * 1024}
                        for i in [1, 0]
                    ],
                    "usage": {"prompt_tokens": 2, "total_tokens": 2},
                },
            )
        return httpx.Response(
            200,
            json={
                "id": "test",
                "created": 1,
                "model": "qwen3.8-flash",
                "object": "chat.completion",
                "choices": [
                    {
                        "index": 0,
                        "finish_reason": "stop",
                        "message": {"role": "assistant", "content": "测试回答"},
                    }
                ],
            },
        )

    model = ModelClient(ModelSettings(api_key=SecretStr("fake-key")), httpx.MockTransport(respond))
    try:
        response = await model.chat(
            ModelRequest(
                messages=(
                    ModelMessage(role="system", content="受控提示"),
                    ModelMessage(role="user", content="测试问题"),
                )
            )
        )
        assert response.message.content == "测试回答"
        assert response.usage is None
        vectors = await model.embed(["文本一", "文本二"])
        assert vectors.vectors[0] == [1.0] * 1024
        assert vectors.usage.input_tokens == 2
        assert payloads[0]["model"] == "qwen3.8-flash"
        assert payloads[0]["enable_thinking"] is False
        assert payloads[0]["temperature"] == 0.2
        assert payloads[0]["max_tokens"] == 2048
        assert payloads[1]["dimensions"] == 1024
        assert payloads[1]["encoding_format"] == "float"
        for texts in [[], [""], ["x"] * 11]:
            with pytest.raises(ValueError, match="批次"):
                await model.embed(texts)
        assert len(payloads) == 2
    finally:
        await model.close()


@pytest.mark.asyncio
@pytest.mark.parametrize("vector", [[0.0] * 1024, [1.0] * 1023, [float("inf")] * 1024])
async def test_invalid_vectors_are_rejected(vector: list[float]) -> None:
    """拒绝零向量、错误维度和非有限值，不截断或填充。"""
    transport = httpx.MockTransport(
        lambda request: httpx.Response(
            200,
            content=json.dumps(
                {
                    "object": "list",
                    "model": "text-embedding-v4",
                    "data": [{"object": "embedding", "index": 0, "embedding": vector}],
                    "usage": {"prompt_tokens": 1, "total_tokens": 1},
                }
            ),
        )
    )
    model = ModelClient(ModelSettings(api_key=SecretStr("fake-key")), transport)
    try:
        with pytest.raises(ValueError, match="维度或数值"):
            await model.embed(["测试"])
    finally:
        await model.close()


@pytest.mark.asyncio
async def test_resource_lifecycle_preserves_password_and_closes_clients(tmp_path: Path) -> None:
    """特殊字符密码不拼接进URL，离线打开/关闭不建表、不发HTTP请求。"""
    settings = connected_settings(
        mysql={"password": "p@ss:#$ /value"},
        observability={"log_directory": str(tmp_path)},
    )
    async with open_resources(settings) as resources:
        assert resources.database.url.password == "p@ss:#$ /value"
        assert not resources.elasticsearch.is_closed
        assert resources.qdrant.headers["api-key"] == "fake-key"
        assert resources.model.settings.api_key.get_secret_value() == "fake-key"
    assert resources.elasticsearch.is_closed
    assert resources.qdrant.is_closed
    assert resources.mineru.is_closed


@pytest.mark.asyncio
@pytest.mark.parametrize("name", ["search_knowledge", "run_shell"])
async def test_model_tool_calls_preserve_usage_and_enforce_allowlist(name: str) -> None:
    """仅接受本轮登记工具，保留真实计量并忽略供应商隐藏推理。"""

    def respond(request: httpx.Request) -> httpx.Response:
        """返回一次真实协议形状的工具建议，不执行任何外部动作。"""
        payload = json.loads(request.content)
        assert payload["tools"][0]["function"]["name"] == "search_knowledge"
        return httpx.Response(
            200,
            json={
                "id": "provider-test",
                "created": 1,
                "model": "qwen3.8-flash",
                "object": "chat.completion",
                "usage": {
                    "prompt_tokens": 20,
                    "completion_tokens": 5,
                    "total_tokens": 25,
                    "prompt_tokens_details": {"cached_tokens": 4},
                },
                "choices": [
                    {
                        "index": 0,
                        "finish_reason": "tool_calls",
                        "message": {
                            "role": "assistant",
                            "content": None,
                            "reasoning_content": "private-reasoning",
                            "tool_calls": [
                                {
                                    "id": "call-1",
                                    "type": "function",
                                    "function": {
                                        "name": name,
                                        "arguments": '{"query":"打印机离线"}',
                                    },
                                }
                            ],
                        },
                    }
                ],
            },
        )

    model = ModelClient(ModelSettings(api_key=SecretStr("fake-key")), httpx.MockTransport(respond))
    request = ModelRequest(
        messages=(ModelMessage(role="user", content="打印机离线"),),
        tools=(
            ModelTool(
                name="search_knowledge",
                description="查询授权知识",
                parameters={
                    "type": "object",
                    "properties": {"query": {"type": "string"}},
                    "required": ["query"],
                    "additionalProperties": False,
                },
            ),
        ),
    )
    try:
        if name == "run_shell":
            with pytest.raises(ModelProtocolError, match="未授权工具") as error:
                await model.chat(request)
            assert error.value.usage.output_tokens == 5
        else:
            response = await model.chat(request)
            assert response.message.tool_calls[0].arguments == {"query": "打印机离线"}
            assert response.usage.model_dump() == {
                "input_tokens": 20,
                "output_tokens": 5,
                "cached_input_tokens": 4,
            }
            assert "private-reasoning" not in response.model_dump_json()
    finally:
        await model.close()


@pytest.mark.asyncio
async def test_truncated_model_response_keeps_confirmed_usage() -> None:
    """输出超长属于失败，但已确认计费不能丢弃或伪装为未知零消耗。"""
    transport = httpx.MockTransport(
        lambda request: httpx.Response(
            200,
            json={
                "id": "limited-response",
                "created": 1,
                "model": "qwen3.8-flash",
                "object": "chat.completion",
                "usage": {"prompt_tokens": 100, "completion_tokens": 2048, "total_tokens": 2148},
                "choices": [
                    {
                        "index": 0,
                        "finish_reason": "length",
                        "message": {"role": "assistant", "content": "private-partial-answer"},
                    }
                ],
            },
        )
    )
    model = ModelClient(ModelSettings(api_key=SecretStr("fake-key")), transport)
    try:
        with pytest.raises(ModelProtocolError, match="输出达到上限") as error:
            await model.chat(ModelRequest(messages=(ModelMessage(role="user", content="test"),)))
        assert error.value.usage.input_tokens == 100
        assert error.value.usage.output_tokens == 2048
        assert error.value.provider_request_id == "limited-response"
        assert "private-partial-answer" not in str(error.value)
    finally:
        await model.close()


@pytest.mark.asyncio
@pytest.mark.parametrize("respond_with_tool", [True, False])
async def test_required_tool_is_enforced_in_provider_request_and_response(respond_with_tool):
    """阶段只收窄白名单并核验供应商实际响应，不能把自由回答当作已取证。"""

    def respond(request):
        payload = json.loads(request.content)
        assert payload["tool_choice"] == {
            "type": "function",
            "function": {"name": "load_procedure"},
        }
        return httpx.Response(
            200,
            json={
                "id": "test",
                "created": 1,
                "model": "qwen3.8-flash",
                "object": "chat.completion",
                "choices": [
                    {
                        "index": 0,
                        "finish_reason": "tool_calls" if respond_with_tool else "stop",
                        "message": {
                            "role": "assistant",
                            "content": None if respond_with_tool else "自行回答",
                            "tool_calls": [
                                {
                                    "id": "tool",
                                    "type": "function",
                                    "function": {"name": "load_procedure", "arguments": "{}"},
                                }
                            ]
                            if respond_with_tool
                            else None,
                        },
                    }
                ],
            },
        )

    client = ModelClient(ModelSettings(api_key=SecretStr("fake-key")), httpx.MockTransport(respond))
    try:
        request = ModelRequest(
            messages=(ModelMessage(role="user", content="读取规程"),),
            tools=(ModelTool(name="load_procedure", description="读取", parameters={}),),
            required_tool="load_procedure",
        )
        if respond_with_tool:
            assert (await client.chat(request)).message.tool_calls[0].name == "load_procedure"
        else:
            with pytest.raises(ModelProtocolError):
                await client.chat(request)
        with pytest.raises(ValidationError):
            ModelRequest(messages=request.messages, required_tool="load_procedure")
    finally:
        await client.close()
