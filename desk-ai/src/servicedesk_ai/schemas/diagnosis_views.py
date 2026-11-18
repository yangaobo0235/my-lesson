"""只读公开投影，版本和计量显式输出十进制字符串，不返回内部会话凭据。作者：yangaobo。"""

from datetime import datetime
from typing import Literal

from servicedesk_ai.schemas.diagnosis import DraftContent, DraftPurpose
from servicedesk_ai.schemas.indexing import Identifier, WireModel


class ConversationView(WireModel):
    """会话管理投影，任务另行保存诊断版本与环境。"""

    id: Identifier
    title: str
    mode: Literal["CONSULT", "CUSTOMER_SELF_SERVICE", "TECHNICIAN_ASSIST"]
    customer_id: Identifier | None
    ticket_id: Identifier | None
    row_version: str
    created_at: datetime
    updated_at: datetime


class CitationView(WireModel):
    """真实标题、版本及定位由持久来源构造，模型只选择编号。"""

    number: int
    source_usage_id: Identifier
    title: str
    locator: dict[str, object]
    excerpt: str
    document_id: Identifier | None = None


class DraftView(WireModel):
    """创建草稿不代表已执行；消费事实只保存在ticket。"""

    id: Identifier
    draft_root_id: Identifier
    revision_number: int
    content_sha256: str
    status: Literal["PENDING", "SUPERSEDED", "WITHDRAWN", "EXPIRED"]
    task_id: Identifier
    diagnosis_context_version: str
    source_run_id: Identifier | None
    purpose: DraftPurpose
    ticket_id: Identifier | None
    ticket_row_version: str | None
    task_state_version: str
    content: DraftContent
    expires_at: datetime


class FeedbackView(WireModel):
    """本人运行评价，不包含共享开关、管理员处理状态或聊天正文。"""

    id: Identifier
    run_id: Identifier
    rating: Literal["HELPFUL", "UNHELPFUL"]
    adoption: Literal["NOT_USED", "ACCEPTED", "MODIFIED", "REJECTED"]
    issue_category: (
        Literal["INCORRECT", "IRRELEVANT", "MISSING_CONTEXT", "UNSUPPORTED", "OTHER"] | None
    )
    comment: str | None
    row_version: str


class PageView[T](WireModel):
    """统一分页上限100，总数按十进制字符串公开。"""

    records: list[T]
    total: str
    page_num: int
    page_size: int


class ModelCallView(WireModel):
    """只输出实际计量与模型，不暴露完整Prompt或隐藏思维链。"""

    number: int
    purpose: str
    status: str
    requested_model: str
    response_model: str | None
    input_tokens: str | None
    output_tokens: str | None
    estimated_cost: str | None


class ToolCallView(WireModel):
    """工具来源失效时隐藏相关摘要，只保留安全调用状态。"""

    number: int
    name: str
    status: str
    result_summary: str | None
    source_usage_ids: list[Identifier]


class CallsView(WireModel):
    """一轮已有调用与用量未知情况，不把估算费用当实际账单。"""

    model_calls: list[ModelCallView]
    tool_calls: list[ToolCallView]
