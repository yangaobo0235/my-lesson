"""提供诊断任务、事实、检查反馈与任务事件HTTP入口。作者：yangaobo。"""

from typing import Annotated

from fastapi import APIRouter, Query, Request

from servicedesk_ai.api.dependencies import (
    Actor,
    BusinessId,
    PageNumber,
    PageSize,
    services,
    success,
)
from servicedesk_ai.schemas.diagnosis_views import PageView
from servicedesk_ai.schemas.result import Result
from servicedesk_ai.schemas.task_views import CheckView, TaskEventPage, TaskFactView, TaskView
from servicedesk_ai.schemas.tasks import (
    CheckAnswerInput,
    TaskClose,
    TaskContextChange,
    TaskCreate,
    TaskFactChange,
    TaskOutcome,
    TaskReceipt,
)

router = APIRouter()


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
