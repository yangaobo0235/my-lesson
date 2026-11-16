"""浏览器私有排障、事实、原键查询、草稿与反馈HTTP适配。作者：yangaobo。"""

from __future__ import annotations

import asyncio
import json
import time
from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import APIRouter, Depends, Path, Query, Request
from fastapi.responses import StreamingResponse

from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.bootstrap.application import ApplicationServices
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.schemas.diagnosis import (
    ConversationChange,
    ConversationCreate,
    DraftCreate,
    FactChange,
    FeedbackHandle,
    FeedbackInput,
    FeedbackShare,
    GenerationCreate,
)
from servicedesk_ai.schemas.diagnosis_views import (
    CallsView,
    ConversationView,
    DraftView,
    FactView,
    FeedbackQueueItem,
    FeedbackView,
    GenerationView,
    PageView,
    SharedFeedbackCase,
)
from servicedesk_ai.schemas.result import Result

router = APIRouter(prefix="/api/v1/ai", tags=["私有辅助排障"])
admin_router = APIRouter(prefix="/api/v1/admin", tags=["知识反馈处置"])
BusinessId = Annotated[str, Path(pattern=r"^[0-9a-f]{32}$")]
PageNumber = Annotated[int, Query(alias="pageNum", ge=1)]
PageSize = Annotated[int, Query(alias="pageSize", ge=1, le=100)]


def services(request: Request) -> ApplicationServices:
    """业务未装配或监听未就绪时明确503，不启动请求内备用任务。"""
    value = getattr(request.app.state, "services", None)
    if value is None:
        raise BusinessError(CommonErrorCode.SERVICE_UNAVAILABLE)
    return value


async def identity(request: Request) -> VerifiedIdentity:
    """原Cookie仅发送user，写请求包含原CSRF，不信任外来身份头。"""
    cookie = request.headers.get("cookie", "")
    if len(cookie) > 8192:
        raise BusinessError(CommonErrorCode.INVALID_REQUEST)
    return await services(request).rpc.identity.verify_browser(
        cookie, request.method, request.headers.get("x-csrf-token")
    )


Actor = Annotated[VerifiedIdentity, Depends(identity)]


def success[T](request: Request, value: T) -> Result[T]:
    """普通JSON统一成功0并保留当前追踪号。"""
    return Result[T](code=0, message="成功", data=value, trace_id=request.state.trace_id)


@router.post("/conversations", status_code=201)
async def create(
    request: Request, actor: Actor, form: ConversationCreate
) -> Result[ConversationView]:
    """创建本人客户自助或绑定工单技术会话。"""
    return success(request, await services(request).conversations.create(actor, form))


@router.get("/conversations")
async def list_conversations(
    request: Request,
    actor: Actor,
    page_num: PageNumber = 1,
    page_size: PageSize = 10,
    mode: Annotated[
        str | None, Query(pattern="^(CUSTOMER_SELF_SERVICE|TECHNICIAN_ASSIST)$")
    ] = None,
    ticket_id: Annotated[str | None, Query(alias="ticketId", pattern="^[0-9a-f]{32}$")] = None,
) -> Result[PageView[ConversationView]]:
    """数据库分页读取本人当前有权会话。"""
    return success(
        request,
        await services(request).conversations.list(actor, page_num, page_size, mode, ticket_id),
    )


@router.get("/conversations/{identifier}")
async def get_conversation(
    request: Request, actor: Actor, identifier: BusinessId
) -> Result[ConversationView]:
    """工单权限不会授予他人的私有会话。"""
    return success(request, await services(request).conversations.get(actor, identifier))


@router.patch("/conversations/{identifier}")
async def rename(
    request: Request, actor: Actor, identifier: BusinessId, form: ConversationChange
) -> Result[ConversationView]:
    """改名比较会话管理版本。"""
    result = await services(request).conversations.change(actor, identifier, form)
    if result is None:
        raise RuntimeError("改名未返回会话")
    return success(request, result)


@router.delete("/conversations/{identifier}")
async def delete(
    request: Request, actor: Actor, identifier: BusinessId, form: ConversationChange
) -> Result[None]:
    """逻辑删除并结束活跃轮，不物理删除来源和正式业务历史。"""
    await services(request).conversations.change(actor, identifier, form, delete=True)
    return success(request, None)


@router.get("/conversations/{identifier}/facts")
async def facts(
    request: Request,
    actor: Actor,
    identifier: BusinessId,
    page_num: PageNumber = 1,
    page_size: PageSize = 10,
) -> Result[PageView[FactView]]:
    """事实更正和撤回历史保留来源标签。"""
    return success(
        request, await services(request).conversations.facts(actor, identifier, page_num, page_size)
    )


@router.post("/conversations/{identifier}/facts")
async def change_fact(
    request: Request, actor: Actor, identifier: BusinessId, form: FactChange
) -> Result[ConversationView]:
    """无活跃轮时按事实版本追加确认、更正、撤回或环境变更。"""
    return success(
        request, await services(request).conversations.fact_change(actor, identifier, form)
    )


@router.post("/conversations/{identifier}/generations", status_code=202)
async def generate(
    request: Request, actor: Actor, identifier: BusinessId, form: GenerationCreate
) -> Result[GenerationView]:
    """只持久受理，不在HTTP进程运行模型。"""
    return success(request, await services(request).conversations.generate(actor, identifier, form))


@router.get("/conversations/{identifier}/turns")
async def turns(
    request: Request,
    actor: Actor,
    identifier: BusinessId,
    page_num: PageNumber = 1,
    page_size: PageSize = 10,
) -> Result[PageView[GenerationView]]:
    """每轮历史交付前重核全部实际来源。"""
    return success(
        request, await services(request).conversations.turns(actor, identifier, page_num, page_size)
    )


@router.get("/generations/by-request/{key}")
async def generation_receipt(
    request: Request, actor: Actor, key: BusinessId
) -> Result[GenerationView]:
    """响应未知沿拥有者原键核对，不自动换键或重跑。"""
    return success(request, await services(request).conversations.by_request(actor, key))


@router.get("/generations/{identifier}")
async def get_generation(
    request: Request, actor: Actor, identifier: BusinessId
) -> Result[GenerationView]:
    """来源失效时只显示拥有者问题和安全状态。"""
    return success(request, await services(request).conversations.get_generation(actor, identifier))


@router.post("/generations/{identifier}/cancel")
async def cancel(request: Request, actor: Actor, identifier: BusinessId) -> Result[GenerationView]:
    """先持久取消并撤销执行权，再由worker结束外部等待。"""
    return success(request, await services(request).conversations.cancel(actor, identifier))


@router.get("/generations/{identifier}/calls")
async def calls(request: Request, actor: Actor, identifier: BusinessId) -> Result[CallsView]:
    """当前有权逐次模型与只读工具调用摘要，未知计量保持null。"""
    return success(request, await services(request).conversations.calls(actor, identifier))


@router.post(
    "/generations/{identifier}/stream",
    response_class=StreamingResponse,
    responses={
        200: {
            "description": (
                "订阅既有运行；JSON事件含generationId、sequence及data，断流后查询原运行。"
            ),
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
    await application.conversations.get_generation(actor, identifier)

    async def events() -> AsyncIterator[str]:
        """心跳和连接序号有界，身份与来源在每次交付重新核验。"""
        sequence = 0
        deadline = time.monotonic() + 180
        previous = None
        tool_versions: dict[int, str] = {}

        def event(kind: str, data: object) -> str:
            """完整JSON事件，正文只从已提交的持久快照取得。"""
            nonlocal sequence
            sequence += 1
            return (
                f"event: {kind}\ndata: "
                + json.dumps(
                    {"generationId": identifier, "sequence": sequence, "data": data},
                    ensure_ascii=False,
                )
                + "\n\n"
            )

        try:
            while time.monotonic() < deadline:
                if await request.is_disconnected():
                    return
                current_actor = await application.rpc.identity.verify_ref(actor.actor)
                value = await application.conversations.get_generation(current_actor, identifier)
                if value.status != previous:
                    yield event("stage", {"status": value.status})
                    previous = value.status
                calls = await application.conversations.calls(current_actor, identifier)
                for tool in calls.tool_calls:
                    if tool_versions.get(tool.number) != tool.status:
                        tool_versions[tool.number] = tool.status
                        yield event("tool", tool.model_dump(mode="json", by_alias=True))
                if value.status not in {"QUEUED", "RUNNING"}:
                    if value.status == "SUCCEEDED" and not value.sources_hidden:
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
            yield event("error", {"code": 1013, "message": "订阅预算已结束，请查询原运行"})
        except asyncio.CancelledError:
            raise
        except Exception:
            yield event("error", {"code": 1011, "message": "订阅暂不可用，请查询原运行"})

    return StreamingResponse(
        events(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"},
    )


@router.post("/conversations/{identifier}/action-drafts", status_code=201)
async def draft(
    request: Request, actor: Actor, identifier: BusinessId, form: DraftCreate
) -> Result[DraftView]:
    """模板化创建人工确认草稿，不额外调用模型。"""
    return success(request, await services(request).drafts.create(actor, identifier, form))


@router.get("/action-drafts/by-request/{key}")
async def draft_receipt(request: Request, actor: Actor, key: BusinessId) -> Result[DraftView]:
    """查询本人原草稿回执，消费事实只由ticket查询。"""
    return success(request, await services(request).drafts.by_request(actor, key))


@router.get("/action-drafts/{identifier}")
async def get_draft(request: Request, actor: Actor, identifier: BusinessId) -> Result[DraftView]:
    """当前来源授权后读取本人草稿。"""
    return success(request, await services(request).drafts.get(actor, identifier))


@router.put("/generations/{identifier}/feedback")
async def feedback(
    request: Request, actor: Actor, identifier: BusinessId, form: FeedbackInput
) -> Result[FeedbackView]:
    """成功运行本人反馈，技术无帮助结果为RECORDED。"""
    return success(request, await services(request).feedback.put(actor, identifier, form))


@router.patch("/generations/{identifier}/feedback/share")
async def feedback_share(
    request: Request, actor: Actor, identifier: BusinessId, form: FeedbackShare
) -> Result[FeedbackView]:
    """仅客户会话显式共享，撤销不抹去已有处理结论。"""
    return success(request, await services(request).feedback.share(actor, identifier, form))


@admin_router.get("/feedback")
async def shared_feedback(
    request: Request,
    actor: Actor,
    page_num: PageNumber = 1,
    page_size: PageSize = 10,
    status: Annotated[str | None, Query(pattern="^(PENDING|RESOLVED|DISMISSED)$")] = None,
) -> Result[PageView[FeedbackQueueItem]]:
    """知识管理员分页查看已共享反馈的处置状态，不返回私有正文。"""
    return success(
        request,
        await services(request).feedback.list_shared(actor, page_num, page_size, status),
    )


@admin_router.get("/feedback/{identifier}")
async def shared_feedback_case(
    request: Request, actor: Actor, identifier: BusinessId
) -> Result[SharedFeedbackCase]:
    """展开前重新核验客户共享和全部来源授权。"""
    return success(request, await services(request).feedback.shared_case(actor, identifier))


@admin_router.post("/feedback/{identifier}/handle")
async def handle_shared_feedback(
    request: Request, actor: Actor, identifier: BusinessId, form: FeedbackHandle
) -> Result[FeedbackQueueItem]:
    """知识管理员用行版本记录解决或驳回，不把反馈自动变成知识。"""
    return success(request, await services(request).feedback.handle(actor, identifier, form))
