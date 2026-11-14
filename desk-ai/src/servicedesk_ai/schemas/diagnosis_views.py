"""只读公开投影，版本和计量显式输出十进制字符串，不返回内部会话凭据。作者：yangaobo。"""

from datetime import datetime
from typing import Literal

from servicedesk_ai.schemas.diagnosis import DiagnosisResult, SupportEnvironment
from servicedesk_ai.schemas.indexing import Identifier, WireModel


class ConversationView(WireModel):
    """私有会话管理与诊断版本分开显示。"""

    id: Identifier
    title: str
    mode: Literal["CUSTOMER_SELF_SERVICE", "TECHNICIAN_ASSIST"]
    customer_id: Identifier
    service_item_id: Identifier | None
    ticket_id: Identifier | None
    device_id: Identifier | None
    support_context: SupportEnvironment
    state_version: str
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


class GenerationView(WireModel):
    """来源失效时隐藏相关正文和引用，保留拥有者问题与安全状态。"""

    id: Identifier
    conversation_id: Identifier
    question: str
    status: Literal["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED", "INTERRUPTED"]
    answer_type: Literal["ANSWERED", "CLARIFICATION", "INSUFFICIENT_EVIDENCE"] | None
    answer: str | None
    structured_result: DiagnosisResult | None
    input_state_version: str
    output_state_version: str | None
    ticket_version: str | None
    sources_hidden: bool
    citations: list[CitationView]
    input_tokens: str | None
    output_tokens: str | None
    error_code: str | None
    created_at: datetime
    finished_at: datetime | None


class FactView(WireModel):
    """当前事实或更正历史明确来源类别，不将用户反馈写成工具实测。"""

    id: Identifier
    state_version: str
    fact_kind: Literal["OBSERVATION", "PERFORMED_ACTION", "OPEN_QUESTION", "HYPOTHESIS"]
    operation: Literal["ASSERT", "RETRACT"]
    active: bool
    statement: str | None
    source_type: Literal["USER_REPORT", "STAFF_RECORD", "TOOL_OBSERVATION", "ASSISTANT_PROPOSAL"]
    supersedes_fact_id: Identifier | None
    source_generation_id: Identifier | None
    sources_hidden: bool
    historical_environment: bool
    created_at: datetime


class DraftContent(WireModel):
    """草稿待人编辑，建议与确认执行分开，不增加模型调用。"""

    schema_version: Literal[1] = 1
    title: str = ""
    symptom: str = ""
    attempted_actions: str = ""
    content: str = ""
    resolution_type: Literal["RESTORED", "GUIDANCE", "REFERRED"] | None = None
    referral_target: str | None = None


class DraftView(WireModel):
    """创建草稿不代表已执行；消费事实只保存在ticket。"""

    id: Identifier
    source_generation_id: Identifier
    purpose: Literal["CREATE_TICKET", "ADD_RECORD", "RESOLVE_TICKET"]
    ticket_id: Identifier | None
    ticket_version: str | None
    state_version: str
    content: DraftContent
    expires_at: datetime


class FeedbackView(WireModel):
    """技术无帮助反馈为RECORDED，不进入知识管理员无权处理队列。"""

    id: Identifier
    rating: Literal["HELPFUL", "UNHELPFUL"]
    issue_category: str | None
    comment: str | None
    share_with_knowledge_admin: bool
    status: Literal["NOT_REQUIRED", "RECORDED", "PENDING", "RESOLVED", "DISMISSED"]
    row_version: str


class FeedbackQueueItem(WireModel):
    """管理队列只含已显式共享的客户反馈元数据，不包含会话正文。"""

    id: Identifier
    generation_id: Identifier
    rating: Literal["UNHELPFUL"]
    issue_category: str
    status: Literal["PENDING", "RESOLVED", "DISMISSED"]
    row_version: str
    created_at: datetime


class FeedbackSourceView(WireModel):
    """管理员展开前已核验当前授权的实际来源摘要。"""

    title: str
    locator: dict[str, object]
    excerpt: str


class SharedFeedbackCase(WireModel):
    """仅当前共享且全部来源仍可访问时返回客户确认公开的本轮内容。"""

    feedback: FeedbackQueueItem
    comment: str | None
    question: str
    answer: str
    sources: list[FeedbackSourceView]


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
