"""封装百炼兼容模型协议，固定请求预算并校验Embedding空间。"""

import json
import math
import ssl
from collections.abc import Iterator
from contextlib import contextmanager
from typing import Literal, Self, cast

import httpx
from openai import APIConnectionError, APIStatusError, AsyncOpenAI, omit
from openai.types.chat import ChatCompletionMessageParam, ChatCompletionToolParam
from opentelemetry import trace
from pydantic import BaseModel, ConfigDict, Field, model_validator

from servicedesk_ai.bootstrap.components import ModelSettings


def compact_schema(value: dict[str, object]) -> dict[str, object]:
    """移除展示注解及根合同不可达的定义，保留实际引用的类型、描述和约束。"""

    def visit(item: object) -> object:
        """字段名即使叫title/default也保留，只清理schema节点的注解。"""
        if isinstance(item, dict):
            return {
                key: {name: visit(child) for name, child in child_value.items()}
                if key in {"properties", "$defs"} and isinstance(child_value, dict)
                else visit(child_value)
                for key, child_value in item.items()
                if key not in {"title", "default"}
            }
        if isinstance(item, list):
            return [visit(child) for child in item]
        return item

    result = cast(dict[str, object], visit(value))
    definitions = result.get("$defs")
    if not isinstance(definitions, dict):
        return result
    reachable: set[str] = set()

    def references(item: object) -> None:
        """沿本地引用闭包遍历，先标记再递归以支持循环定义。"""
        if isinstance(item, list):
            for child in item:
                references(child)
        elif isinstance(item, dict):
            reference = item.get("$ref")
            if isinstance(reference, str) and reference.startswith("#/$defs/"):
                name = reference.removeprefix("#/$defs/").split("/", 1)[0]
                name = name.replace("~1", "/").replace("~0", "~")
                if name in definitions and name not in reachable:
                    reachable.add(name)
                    references(definitions[name])
            for child in item.values():
                references(child)

    references({key: item for key, item in result.items() if key != "$defs"})
    if reachable:
        result["$defs"] = {name: item for name, item in definitions.items() if name in reachable}
    else:
        result.pop("$defs")
    return result


class ModelContract(BaseModel):
    """模型边界使用自有类型，校验错误不回显上下文和工具参数。"""

    model_config = ConfigDict(extra="forbid", frozen=True, hide_input_in_errors=True)


class ModelToolCall(ModelContract):
    """供应商提出的函数调用，仅表示建议，不证明工具已经执行。"""

    id: str = Field(min_length=1)
    name: str = Field(pattern=r"^[a-z_][a-z0-9_]{0,63}$")
    arguments: dict[str, object] = Field(repr=False)


class ModelMessage(ModelContract):
    """可往返的公开消息，不包含隐藏推理字段。"""

    role: Literal["system", "user", "assistant", "tool"]
    content: str | None = Field(default=None, repr=False)
    tool_calls: tuple[ModelToolCall, ...] = ()
    tool_call_id: str | None = None

    @model_validator(mode="after")
    def validate_role(self) -> "ModelMessage":
        """禁止将工具回执伪装为用户消息或混用调用身份。"""
        if (self.role == "tool") != (self.tool_call_id is not None):
            raise ValueError("工具回执必须携带对应调用标识")
        if self.tool_calls and self.role != "assistant":
            raise ValueError("仅助手消息可以提出工具调用")
        if not self.tool_calls and (self.content is None or not self.content.strip()):
            raise ValueError("消息正文不能为空")
        return self


class ModelTool(ModelContract):
    """本轮允许模型选择的函数定义，执行权限仍由用例核验。"""

    name: str = Field(pattern=r"^[a-z_][a-z0-9_]{0,63}$")
    description: str = Field(min_length=1)
    parameters: dict[str, object] = Field(repr=False)


class ModelRequest(ModelContract):
    """一次模型调用的明确上下文与工具白名单。"""

    messages: tuple[ModelMessage, ...] = Field(min_length=1, repr=False)
    tools: tuple[ModelTool, ...] = ()
    result_schema: dict[str, object] | None = Field(default=None, repr=False)
    model: str | None = Field(default=None, min_length=1, max_length=200)
    max_output_tokens: int | None = Field(default=None, ge=1, le=8192)
    temperature: float | None = Field(default=None, ge=0, le=2)
    required_tool: str | None = Field(default=None, pattern=r"^[a-z_][a-z0-9_]{0,63}$")
    parallel_tool_calls: bool = True

    @model_validator(mode="after")
    def required_tool_is_available(self) -> Self:
        """阶段约束只能收窄当前工具白名单，不能凭名称新增工具。"""
        if self.required_tool is not None and self.required_tool not in {
            tool.name for tool in self.tools
        }:
            raise ValueError("阶段要求的工具不在本轮白名单")
        return self


class ModelUsage(ModelContract):
    """仅保存供应商实际返回的计量，缺失字段保持未知。"""

    input_tokens: int | None = Field(default=None, ge=0)
    output_tokens: int | None = Field(default=None, ge=0)
    cached_input_tokens: int | None = Field(default=None, ge=0)


class ModelResponse(ModelContract):
    """公开正文、函数建议及计量，不向用例暴露SDK响应。"""

    message: ModelMessage = Field(repr=False)
    usage: ModelUsage | None = None
    provider_request_id: str | None = None
    model: str | None = None
    finish_reason: Literal["stop", "tool_calls"]


class EmbeddingResponse(ModelContract):
    """经过空间合同验证的向量与实际供应商计量。"""

    vectors: list[list[float]] = Field(repr=False)
    usage: ModelUsage | None = None
    provider_request_id: str | None = None
    model: str | None = None


class ModelProtocolError(ValueError):
    """已收到供应商响应但内容不合格，仍携带可确认用量供调用账目保存。"""

    def __init__(
        self,
        message: str,
        usage: ModelUsage | None,
        provider_request_id: str | None = None,
        model: str | None = None,
    ) -> None:
        """仅暴露受控诊断和计量，不携带原始提示词、正文或隐藏推理。"""
        super().__init__(message)
        self.usage = usage
        self.provider_request_id = provider_request_id
        self.model = model


class ToolProposalRejected(ModelProtocolError):
    """整批工具提议未通过本次白名单；没有工具被执行，可终止取证而不能重试该提议。"""


class ModelProviderError(RuntimeError):
    """供应商明确拒绝或传输不可用；不携带SDK正文、请求头和认证信息。"""

    def __init__(self, reason: str, *, rejected: bool) -> None:
        """明确拒绝可记FAILED，连接/服务端故障仍保留结果未知及未知计量。"""
        super().__init__("模型服务暂不可用")
        self.reason = reason
        self.rejected = rejected


@contextmanager
def provider_boundary() -> Iterator[None]:
    """将SDK错误收敛为受控原因，不重试，不向用例传播供应商原始响应。"""
    try:
        yield
    except APIStatusError as error:
        reason = (
            "ACCOUNT_UNAVAILABLE"
            if error.code in {"Arrearage", "InsufficientBalance", "insufficient_quota"}
            else "AUTHENTICATION_REJECTED"
            if error.status_code in {401, 403}
            else "RATE_LIMITED"
            if error.status_code == 429
            else "REQUEST_REJECTED"
            if 400 <= error.status_code < 500
            else "SERVICE_UNAVAILABLE"
        )
        raise ModelProviderError(
            reason, rejected=400 <= error.status_code < 500 and error.status_code != 408
        ) from None
    except APIConnectionError:
        raise ModelProviderError("TRANSPORT_UNAVAILABLE", rejected=False) from None


class ModelClient:
    """按进程复用模型连接，不重试付费生成，不在初始化时调用模型。"""

    def __init__(
        self,
        settings: ModelSettings,
        transport: httpx.AsyncBaseTransport | None = None,
        tracer: trace.Tracer | None = None,
    ) -> None:
        """创建客户端；测试可替换传输，生产保持TLS验证。"""
        self.settings = settings
        self._tracer = tracer or trace.get_tracer(__name__)
        self._client = AsyncOpenAI(
            api_key=settings.api_key.get_secret_value(),
            base_url=settings.base_url,
            max_retries=0,
            http_client=httpx.AsyncClient(
                timeout=httpx.Timeout(settings.timeout_seconds, connect=2),
                limits=httpx.Limits(max_connections=4, max_keepalive_connections=4),
                follow_redirects=False,
                trust_env=False,
                verify=ssl.create_default_context(),
                transport=transport,
            ),
        )

    async def chat(self, request: ModelRequest) -> ModelResponse:
        """请求单次公开输出或函数建议，不自动执行工具和付费重试。"""
        messages = []
        for message in request.messages:
            wire: dict[str, object] = {"role": message.role, "content": message.content}
            if message.tool_call_id is not None:
                wire["tool_call_id"] = message.tool_call_id
            if message.tool_calls:
                wire["tool_calls"] = [
                    {
                        "id": call.id,
                        "type": "function",
                        "function": {
                            "name": call.name,
                            "arguments": json.dumps(call.arguments, ensure_ascii=False),
                        },
                    }
                    for call in message.tool_calls
                ]
            messages.append(cast(ChatCompletionMessageParam, wire))
        tools = [
            cast(ChatCompletionToolParam, {"type": "function", "function": tool.model_dump()})
            for tool in request.tools
        ]
        with (
            provider_boundary(),
            self._tracer.start_as_current_span(
                "model.chat",
                record_exception=False,
                set_status_on_exception=False,
            ),
        ):
            response = await self._client.chat.completions.create(
                model=request.model or self.settings.chat_model,
                messages=messages,
                tools=tools if tools else omit,
                parallel_tool_calls=request.parallel_tool_calls if tools else omit,
                tool_choice={"type": "function", "function": {"name": request.required_tool}}
                if request.required_tool is not None
                else omit,
                response_format=(
                    {
                        "type": "json_schema",
                        "json_schema": {
                            "name": "diagnosis_result",
                            "strict": True,
                            "schema": request.result_schema,
                        },
                    }
                    if request.result_schema is not None
                    else omit
                ),
                temperature=self.settings.temperature
                if request.temperature is None
                else request.temperature,
                max_tokens=request.max_output_tokens or self.settings.max_output_tokens,
                extra_body={"enable_thinking": self.settings.enable_thinking},
            )
        usage = response.usage
        metering = (
            None
            if usage is None
            else ModelUsage(
                input_tokens=usage.prompt_tokens,
                output_tokens=usage.completion_tokens,
                cached_input_tokens=None
                if usage.prompt_tokens_details is None
                else usage.prompt_tokens_details.cached_tokens,
            )
        )

        def invalid(message: str) -> ModelProtocolError:
            """失败结果不能丢弃已确认的供应商计量。"""
            return ModelProtocolError(message, metering, response.id, response.model)

        if len(response.choices) == 1 and response.choices[0].finish_reason == "length":
            raise invalid("模型输出达到上限，未正常完成生成")
        if len(response.choices) != 1 or response.choices[0].finish_reason not in {
            "stop",
            "tool_calls",
        }:
            raise invalid("模型未正常完成生成")
        choice = response.choices[0]
        calls = []
        allowed = {tool.name for tool in request.tools}
        for call in choice.message.tool_calls or []:
            if call.type != "function" or call.function.name not in allowed:
                raise ToolProposalRejected(
                    "模型提出未授权工具", metering, response.id, response.model
                )
            try:
                arguments = json.loads(call.function.arguments)
            except json.JSONDecodeError:
                raise invalid("模型工具参数不是有效JSON") from None
            if not isinstance(arguments, dict):
                raise invalid("模型工具参数必须为对象")
            calls.append(ModelToolCall(id=call.id, name=call.function.name, arguments=arguments))
        if (choice.finish_reason == "tool_calls") != bool(calls):
            raise invalid("模型结束原因与工具调用不一致")
        if len({call.id for call in calls}) != len(calls):
            raise invalid("模型工具调用标识重复")
        if not request.parallel_tool_calls and len(calls) > 1:
            raise invalid("当前付费取证阶段只允许一个工具提议")
        if request.required_tool is not None and (
            not calls or any(call.name != request.required_tool for call in calls)
        ):
            raise invalid("模型未遵循当前取证阶段约束")
        if not calls and not (choice.message.content and choice.message.content.strip()):
            raise invalid("模型返回空正文")
        return ModelResponse(
            message=ModelMessage(
                role="assistant", content=choice.message.content, tool_calls=tuple(calls)
            ),
            usage=metering,
            provider_request_id=response.id,
            model=response.model,
            finish_reason=cast(Literal["stop", "tool_calls"], choice.finish_reason),
        )

    async def embed(self, texts: list[str]) -> EmbeddingResponse:
        """一次处理至多10条非空文本，完整验证数量、索引、维度和数值。"""
        if not 1 <= len(texts) <= self.settings.embedding_batch_size or any(
            not text.strip() for text in texts
        ):
            raise ValueError("Embedding批次必须包含1至10条非空文本")
        with (
            provider_boundary(),
            self._tracer.start_as_current_span(
                "model.embedding",
                record_exception=False,
                set_status_on_exception=False,
            ),
        ):
            response = await self._client.embeddings.create(
                model=self.settings.embedding_model,
                input=texts,
                dimensions=self.settings.dimensions,
                encoding_format="float",
            )
        ordered = sorted(response.data, key=lambda item: item.index)
        metering = (
            None
            if response.usage is None
            else ModelUsage(input_tokens=response.usage.prompt_tokens, output_tokens=0)
        )
        if [item.index for item in ordered] != list(range(len(texts))):
            raise ModelProtocolError("Embedding响应数量或索引不匹配", metering)
        vectors = [item.embedding for item in ordered]
        if any(
            len(vector) != self.settings.dimensions
            or not all(math.isfinite(value) for value in vector)
            or not any(value != 0 for value in vector)
            for vector in vectors
        ):
            raise ModelProtocolError("Embedding响应维度或数值不符合空间合同", metering)
        return EmbeddingResponse(
            vectors=vectors,
            usage=metering,
            model=response.model,
        )

    async def close(self) -> None:
        """关闭模型连接池。"""
        await self._client.close()
