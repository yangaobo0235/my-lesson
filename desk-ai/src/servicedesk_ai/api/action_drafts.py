"""提供人工确认草稿的读取、修订、撤回与正式回执核对入口。作者：yangaobo。"""

from fastapi import APIRouter, Request

from servicedesk_ai.api.dependencies import Actor, BusinessId, services, success
from servicedesk_ai.schemas.diagnosis import DraftCreate, DraftRevision, DraftWithdraw
from servicedesk_ai.schemas.diagnosis_views import DraftView
from servicedesk_ai.schemas.result import Result
from servicedesk_ai.schemas.tasks import TaskAction, TaskReceipt

router = APIRouter()


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
