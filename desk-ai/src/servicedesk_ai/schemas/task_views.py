"""诊断任务、事实、检查和事件的公开读取合同。作者：yangaobo。"""

from datetime import datetime
from typing import Literal

from servicedesk_ai.schemas.indexing import Identifier, WireModel
from servicedesk_ai.schemas.tasks import (
    AnswerSchema,
    CheckStatus,
    FactKind,
    HypothesisState,
    NormalizedAnswer,
    SafetyCondition,
    TaskEnvironment,
    TaskEventDetails,
    TaskStatus,
)


class TaskView(WireModel):
    """当前任务投影，来源受限时只保留用户目标和安全状态。"""

    id: Identifier
    conversation_id: Identifier
    service_item_id: Identifier | None
    device_id: Identifier | None
    goal: str
    status: TaskStatus
    task_state_version: str
    diagnosis_context_version: str
    row_version: str
    support_context: TaskEnvironment
    hypotheses: HypothesisState
    sources_hidden: bool
    blocking_draft_root_id: Identifier | None
    handover_ticket_id: Identifier | None
    outcome_source: str | None
    outcome_note: str | None
    created_at: datetime
    updated_at: datetime


class TaskFactView(WireModel):
    """原始反馈和更正链保留，工具观测派生正文需重核来源。"""

    id: Identifier
    state_version: str
    fact_kind: FactKind
    operation: Literal["ASSERT", "CORRECT", "RETRACT"]
    statement: str | None
    reported_conditions: list[SafetyCondition]
    source_type: Literal["USER_REPORT", "HUMAN_RECORD", "TOOL_OBSERVATION"]
    message_id: Identifier | None
    supersedes_fact_id: Identifier | None
    active: bool
    historical_environment: bool
    sources_hidden: bool
    created_at: datetime


class CheckView(WireModel):
    """人工ANSWERED表示收到答复；未执行、未恢复、报告恢复分别显示。"""

    id: Identifier
    proposed_run_id: Identifier
    check_key: str
    check_kind: Literal["CLARIFICATION", "CHECK"]
    attempt_number: int
    instruction: str | None
    answer_schema: AnswerSchema | None
    status: CheckStatus
    executor_type: Literal["CUSTOMER", "TECHNICIAN", "TOOL"]
    context_version: str
    row_version: str
    normalized_answer: NormalizedAnswer | None
    answer_message_id: Identifier | None
    previous_check_id: Identifier | None
    sources_hidden: bool
    expires_at: datetime | None


class TaskEventView(WireModel):
    """不可变任务事件及原回执；来源失效时隐藏受影响的派生内容。"""

    id: Identifier
    state_version: str
    actor_type: Literal["USER", "WORKER", "SYSTEM"]
    event_type: str
    details: TaskEventDetails | None
    sources_hidden: bool
    created_at: datetime


class TaskEventPage(WireModel):
    """事件按版本游标有界读取，避免长任务一次加载全部历史。"""

    records: list[TaskEventView]
    next_cursor: str
    has_more: bool
