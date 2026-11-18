"""提供运行受理、消息及调用账目读取与SSE订阅。作者：yangaobo。"""

import asyncio
import json
import logging
import time
from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import APIRouter, Query, Request
from fastapi.responses import StreamingResponse

from servicedesk_ai.api.dependencies import (
    Actor,
    BusinessId,
    PageNumber,
    PageSize,
    services,
    success,
)
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.infrastructure.identity import IdentityFailure
from servicedesk_ai.schemas.diagnosis_views import CallsView, PageView, ToolCallView
from servicedesk_ai.schemas.result import Result
from servicedesk_ai.schemas.run_views import MessagePage, RunStageView, RunView
from servicedesk_ai.schemas.runs import RunCreate

router = APIRouter()
LOGGER = logging.getLogger(__name__)


@router.post("/conversations/{identifier}/runs", status_code=202)
async def create_run(
    request: Request, actor: Actor, identifier: BusinessId, form: RunCreate
) -> Result[RunView]:
    """只持久受理，反馈消息不因排队失败丢失。"""
    value = await services(request).runs.create(actor, identifier, form)
    return success(request, await services(request).run_reads.get(actor, value.id))


@router.get("/conversations/{identifier}/messages")
async def messages(
    request: Request,
    actor: Actor,
    identifier: BusinessId,
    after_sequence: Annotated[int, Query(alias="afterSequence", ge=0)] = 0,
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
) -> Result[MessagePage]:
    """按不可变序号增量获取消息，重连不发新模型请求。"""
    return success(
        request,
        await services(request).run_reads.messages(actor, identifier, after_sequence, limit),
    )


@router.get("/conversations/{identifier}/runs")
async def list_runs(
    request: Request,
    actor: Actor,
    identifier: BusinessId,
    page_num: PageNumber = 1,
    page_size: PageSize = 10,
) -> Result[PageView[RunView]]:
    """只读执行历史，不重新受理模型请求。"""
    return success(
        request,
        await services(request).run_reads.list_for_conversation(
            actor, identifier, page_num, page_size
        ),
    )


@router.get("/runs/by-request/{key}")
async def run_receipt(request: Request, actor: Actor, key: BusinessId) -> Result[RunView]:
    """响应未知沿拥有者原键核对，不自动换键或重跑。"""
    return success(request, await services(request).run_reads.by_request(actor, key))


@router.get("/runs/{identifier}")
async def get_run(request: Request, actor: Actor, identifier: BusinessId) -> Result[RunView]:
    """来源失效时只显示拥有者问题和安全状态。"""
    return success(request, await services(request).run_reads.get(actor, identifier))


@router.post("/runs/{identifier}/cancel")
async def cancel(request: Request, actor: Actor, identifier: BusinessId) -> Result[RunView]:
    """先持久取消并撤销执行权，再由worker结束外部等待。"""
    await services(request).runs.cancel(actor, identifier)
    return success(request, await services(request).run_reads.get(actor, identifier))


@router.get("/runs/{identifier}/calls")
async def calls(request: Request, actor: Actor, identifier: BusinessId) -> Result[CallsView]:
    """当前有权逐次模型与只读工具调用摘要，未知计量保持null。"""
    return success(request, await services(request).run_reads.calls(actor, identifier))


@router.post(
    "/runs/{identifier}/stream",
    response_class=StreamingResponse,
    responses={
        200: {
            "description": ("订阅既有运行；JSON事件含runId、sequence及data，断流后查询原运行。"),
            "content": {"text/event-stream": {"schema": {"type": "string"}}},
            "headers": {
                "Cache-Control": {"schema": {"type": "string"}, "description": "no-store"},
                "X-Accel-Buffering": {"schema": {"type": "string"}, "description": "no"},
            },
        }
    },
)
async def stream(request: Request, actor: Actor, identifier: BusinessId) -> StreamingResponse:
    """只订阅已存在运行；断流不判成功，不触发付费重试。"""
    application = services(request)
    await application.run_reads.get(actor, identifier)

    async def events() -> AsyncIterator[str]:
        """心跳和连接序号有界，身份与来源在每次交付重新核验。"""
        sequence = 0
        deadline = time.monotonic() + 180
        previous: RunStageView | None = None
        tool_versions: dict[int, ToolCallView] = {}

        def event(kind: str, data: object) -> str:
            """完整JSON事件，正文只从已提交的持久快照取得。"""
            nonlocal sequence
            sequence += 1
            return (
                f"event: {kind}\ndata: "
                + json.dumps(
                    {"runId": identifier, "sequence": sequence, "data": data},
                    ensure_ascii=False,
                )
                + "\n\n"
            )

        try:
            while time.monotonic() < deadline:
                if await request.is_disconnected():
                    return
                current_actor = await application.rpc.identity.verify_ref(actor.actor)
                value, tools, task = await application.run_reads.subscription_snapshot(
                    current_actor, identifier
                )
                stage = RunStageView(
                    status=value.status,
                    execution_phase=value.execution_phase,
                    iteration_number=value.iteration_number,
                )
                if stage != previous:
                    yield event("stage", stage.model_dump(mode="json", by_alias=True))
                    previous = stage
                for tool in tools:
                    if tool_versions.get(tool.number) != tool:
                        tool_versions[tool.number] = tool
                        yield event("tool", tool.model_dump(mode="json", by_alias=True))
                if value.status not in {"QUEUED", "RUNNING"}:
                    if value.status == "SUCCEEDED" and not value.sources_hidden:
                        if task is not None:
                            yield event("task", task.model_dump(mode="json", by_alias=True))
                        yield event(
                            "answer", {"answer": value.answer, "answerType": value.answer_type}
                        )
                        yield event(
                            "citations",
                            [
                                item.model_dump(mode="json", by_alias=True)
                                for item in value.citations
                            ],
                        )
                    yield event(
                        "done", {"status": value.status, "sourcesHidden": value.sources_hidden}
                    )
                    return
                yield ": heartbeat\n\n"
                await asyncio.sleep(2)
            yield event(
                "error",
                {
                    "code": CommonErrorCode.DEPENDENCY_TIMEOUT.code,
                    "message": "订阅预算已结束，请查询原运行",
                },
            )
        except asyncio.CancelledError:
            raise
        except (BusinessError, IdentityFailure) as failure:
            code = failure.error.code if isinstance(failure, BusinessError) else failure.code
            yield event("error", {"code": code, "message": "订阅已结束，请查询原运行"})
        except Exception as failure:
            LOGGER.warning(
                "运行订阅异常结束 runId=%s failureType=%s", identifier, type(failure).__name__
            )
            yield event(
                "error",
                {
                    "code": CommonErrorCode.SERVICE_UNAVAILABLE.code,
                    "message": "订阅暂不可用，请查询原运行",
                },
            )

    return StreamingResponse(
        events(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"},
    )
