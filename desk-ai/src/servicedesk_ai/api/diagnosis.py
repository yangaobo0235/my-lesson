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
    DraftRevision,
    DraftWithdraw,
    FeedbackInput,
)
from servicedesk_ai.schemas.diagnosis_views import (
    CallsView,
    ConversationView,
    DraftView,
    FeedbackView,
    PageView,
)
from servicedesk_ai.schemas.result import Result
from servicedesk_ai.schemas.run_views import MessagePage, RunView
from servicedesk_ai.schemas.runs import RunCreate
from servicedesk_ai.schemas.task_views import CheckView, TaskEventPage, TaskFactView, TaskView
from servicedesk_ai.schemas.tasks import (
    CheckAnswerInput,
    TaskAction,
    TaskClose,
    TaskContextChange,
    TaskCreate,
    TaskFactChange,
    TaskOutcome,
    TaskReceipt,
)

router = APIRouter(prefix="/api/v1/ai", tags=["私有辅助排障"])
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


@router.post("/conversations/{identifier}/tasks", status_code=201)
async def create_task(
    request: Request, actor: Actor, identifier: BusinessId, form: TaskCreate
) -> Result[TaskReceipt]:
    """创建一个明确诊断目标及原始反馈，不隐式发起模型。"""
    return success(request, await services(request).tasks.create(actor, identifier, form))


@router.get("/conversations/{identifier}/tasks")
async def list_tasks(
    request: Request,
    actor: Actor,
    identifier: BusinessId,
    page_num: PageNumber = 1,
    page_size: PageSize = 10,
) -> Result[PageView[TaskView]]:
    """同会话多个目标各自保留任务身份与历史。"""
    return success(
        request, await services(request).task_reads.list(actor, identifier, page_num, page_size)
    )


@router.get("/tasks/{identifier}")
async def get_task(request: Request, actor: Actor, identifier: BusinessId) -> Result[TaskView]:
    """返回当前任务版本、状态和来源化假设。"""
    return success(request, await services(request).task_reads.get(actor, identifier))


@router.get("/tasks/{identifier}/facts")
async def facts(
    request: Request,
    actor: Actor,
    identifier: BusinessId,
    page_num: PageNumber = 1,
    page_size: PageSize = 10,
) -> Result[PageView[TaskFactView]]:
    """原始事实与更正链区分展示，模型假设不冒充观测。"""
    return success(
        request, await services(request).task_reads.facts(actor, identifier, page_num, page_size)
    )


@router.post("/tasks/{identifier}/facts")
async def change_fact(
    request: Request, actor: Actor, identifier: BusinessId, form: TaskFactChange
) -> Result[TaskReceipt]:
    """按原动作键和任务版本追加、更正或撤回事实。"""
    return success(request, await services(request).tasks.change_fact(actor, identifier, form))


@router.post("/tasks/{identifier}/context")
async def change_context(
    request: Request, actor: Actor, identifier: BusinessId, form: TaskContextChange
) -> Result[TaskReceipt]:
    """技术任务只同步可信工单环境，自助环境保存本人来源。"""
    return success(
        request, await services(request).tasks.synchronize_context(actor, identifier, form)
    )


@router.get("/tasks/{identifier}/checks")
async def checks(
    request: Request,
    actor: Actor,
    identifier: BusinessId,
    page_num: PageNumber = 1,
    page_size: PageSize = 10,
) -> Result[PageView[CheckView]]:
    """检查、实际反馈和是否执行分开显示。"""
    return success(
        request, await services(request).task_reads.checks(actor, identifier, page_num, page_size)
    )


@router.post("/tasks/{identifier}/checks/{check_id}/answer")
async def answer_check(
    request: Request,
    actor: Actor,
    identifier: BusinessId,
    check_id: BusinessId,
    form: CheckAnswerInput,
) -> Result[TaskReceipt]:
    """先持久化反馈再返回输入消息身份，下一轮由用户明确发起。"""
    return success(request, await services(request).tasks.answer(actor, identifier, check_id, form))


@router.get("/tasks/{identifier}/events")
async def task_events(
    request: Request,
    actor: Actor,
    identifier: BusinessId,
    after_state_version: Annotated[int, Query(alias="afterStateVersion", ge=0)] = 0,
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
) -> Result[TaskEventPage]:
    """按任务版本获取有界事件历史，重复读取不改变状态。"""
    return success(
        request,
        await services(request).task_reads.events(actor, identifier, after_state_version, limit),
    )


@router.post("/tasks/{identifier}/close")
async def close_task(
    request: Request, actor: Actor, identifier: BusinessId, form: TaskClose
) -> Result[TaskReceipt]:
    """结束诊断任务，不自动关闭正式工单。"""
    return success(request, await services(request).tasks.close(actor, identifier, form, "CLOSED"))


@router.post("/tasks/{identifier}/cancel")
async def cancel_task(
    request: Request, actor: Actor, identifier: BusinessId, form: TaskClose
) -> Result[TaskReceipt]:
    """取消任务须无活动Run，剩余检查和草稿同步撤回。"""
    return success(
        request, await services(request).tasks.close(actor, identifier, form, "CANCELLED")
    )


@router.post("/tasks/{identifier}/report-outcome")
async def report_outcome(
    request: Request, actor: Actor, identifier: BusinessId, form: TaskOutcome
) -> Result[TaskReceipt]:
    """明确登记人工报告恢复，不冒充工具实测。"""
    return success(
        request, await services(request).tasks.close(actor, identifier, form, "RESOLVED_REPORTED")
    )


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
        str | None, Query(pattern="^(CONSULT|CUSTOMER_SELF_SERVICE|TECHNICIAN_ASSIST)$")
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
        request, await services(request).run_reads.list(actor, identifier, page_num, page_size)
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
        previous = None
        tool_versions: dict[int, str] = {}

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
                value = await application.run_reads.get(current_actor, identifier)
                if value.status != previous:
                    yield event("stage", {"status": value.status})
                    previous = value.status
                calls = await application.run_reads.calls(current_actor, identifier)
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


@router.post("/tasks/{identifier}/action-drafts", status_code=201)
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


@router.put("/runs/{identifier}/feedback")
async def feedback(
    request: Request, actor: Actor, identifier: BusinessId, form: FeedbackInput
) -> Result[FeedbackView]:
    """保存本人终态运行评价，失败、中断和取消同样可评价。"""
    return success(request, await services(request).feedback.put(actor, identifier, form))


@router.get("/runs/{identifier}/feedback")
async def get_feedback(
    request: Request, actor: Actor, identifier: BusinessId
) -> Result[FeedbackView | None]:
    """读取本人的原评价；未提交过评价时返回null。"""
    return success(request, await services(request).feedback.get(actor, identifier))


@router.post("/action-drafts/{identifier}/revisions", status_code=201)
async def revise_draft(
    request: Request, actor: Actor, identifier: BusinessId, form: DraftRevision
) -> Result[DraftView]:
    """编辑创建不可变修订，原内容不覆盖。"""
    return success(request, await services(request).drafts.revise(actor, identifier, form))


@router.post("/action-drafts/{identifier}/withdraw")
async def withdraw_draft(
    request: Request, actor: Actor, identifier: BusinessId, form: DraftWithdraw
) -> Result[DraftView]:
    """撤回不替代Java的正式动作对账。"""
    return success(request, await services(request).drafts.withdraw(actor, identifier, form))


@router.post("/action-drafts/{identifier}/synchronize")
async def synchronize_draft(
    request: Request, actor: Actor, identifier: BusinessId, form: TaskAction
) -> Result[TaskReceipt]:
    """用户明确核对正式动作结果，AI只读取Java回执并更新任务投影。"""
    return success(request, await services(request).drafts.synchronize(actor, identifier, form))
