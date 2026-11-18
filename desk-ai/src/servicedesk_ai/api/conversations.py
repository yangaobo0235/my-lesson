"""提供私有会话的创建、查询和管理HTTP入口。作者：yangaobo。"""

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
from servicedesk_ai.schemas.diagnosis import ConversationChange, ConversationCreate
from servicedesk_ai.schemas.diagnosis_views import ConversationView, PageView
from servicedesk_ai.schemas.result import Result

router = APIRouter()


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
