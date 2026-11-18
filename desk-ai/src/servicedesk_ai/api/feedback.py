"""提供运行拥有者的评价查询和提交入口。作者：yangaobo。"""

from fastapi import APIRouter, Request

from servicedesk_ai.api.dependencies import Actor, BusinessId, services, success
from servicedesk_ai.schemas.diagnosis import FeedbackInput
from servicedesk_ai.schemas.diagnosis_views import FeedbackView
from servicedesk_ai.schemas.result import Result

router = APIRouter()


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
