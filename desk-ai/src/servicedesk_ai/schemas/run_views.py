"""Run与独立消息公开投影，身份凭据、完整Prompt和隐藏推理永不对外。作者：yangaobo。"""

from datetime import datetime
from typing import Literal

from servicedesk_ai.schemas.diagnosis_views import CitationView
from servicedesk_ai.schemas.indexing import Identifier, WireModel
from servicedesk_ai.schemas.runs import RunResult


class RunView(WireModel):
    """已持久结果及安全状态；隐藏来源时不能返回派生正文。"""

    id: Identifier
    conversation_id: Identifier
    task_id: Identifier | None
    input_message_id: Identifier
    output_message_id: Identifier | None
    status: Literal["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED", "INTERRUPTED"]
    execution_phase: Literal["CONTEXT", "MODEL", "TOOL", "COMMIT"]
    iteration_number: int
    question: str
    answer: str | None
    answer_type: Literal["ANSWERED", "CLARIFICATION", "INSUFFICIENT_EVIDENCE", "HANDOVER"] | None
    structured_result: RunResult | None
    input_state_version: str | None
    output_state_version: str | None
    diagnosis_context_version: str | None
    current_diagnosis_context_version: str | None
    ticket_row_version: str | None
    ticket_event_sequence: str | None
    sources_hidden: bool
    safe_reason: str | None
    stale: bool
    citations: list[CitationView]
    input_tokens: str | None
    output_tokens: str | None
    usage_unknown: bool
    error_code: str | None
    created_at: datetime
    finished_at: datetime | None


class MessageView(WireModel):
    """聊天消息有独立身份和序号，助手正文通过其Run全部依赖授权。"""

    id: Identifier
    task_id: Identifier | None
    sequence_number: str
    role: Literal["USER", "ASSISTANT", "SYSTEM"]
    content: str | None
    run_id: Identifier | None
    sources_hidden: bool
    created_at: datetime


class MessagePage(WireModel):
    """按序号增量读取，数量和UTF-8字节双限，不用当前页计算旧消息身份。"""

    records: list[MessageView]
    next_cursor: str
    has_more: bool
