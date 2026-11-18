"""持久诊断任务、检查反馈和有界假设合同；不接受模型伪造执行事实。作者：yangaobo。"""

from __future__ import annotations

import math
from decimal import Decimal
from typing import Annotated, Literal, Self

from pydantic import (
    AwareDatetime,
    Field,
    StrictBool,
    StrictFloat,
    StrictInt,
    StrictStr,
    model_validator,
)

from servicedesk_ai.schemas.diagnosis import DiagnosisInput, SupportEnvironment, Version
from servicedesk_ai.schemas.indexing import Identifier, WireModel

TaskStatus = Literal[
    "ACTIVE",
    "WAITING_USER",
    "WAITING_APPROVAL",
    "BLOCKED_EXTERNAL",
    "RESOLVED_REPORTED",
    "HANDED_OFF",
    "CLOSED",
    "CANCELLED",
]
SafetyCondition = Literal[
    "SAFETY_HAZARD_REPORTED",
    "DATA_LOSS_RISK_REPORTED",
    "ADMIN_PRIVILEGE_REQUIRED",
    "OUT_OF_SERVICE_SCOPE",
    "NO_PROGRESS_LIMIT",
    "EVIDENCE_CONFLICT",
]
CheckStatus = Literal[
    "OPEN",
    "ANSWERED",
    "OBSERVED_DONE",
    "FAILED",
    "SKIPPED",
    "WITHDRAWN",
    "EXPIRED",
    "NEEDS_RECHECK",
]
EnvironmentField = Literal[
    "os_name",
    "os_version",
    "software_name",
    "software_version",
    "connection_type",
    "affected_scope",
    "error_code",
]
FactKind = Literal["SYMPTOM", "ENVIRONMENT", "ACTION_REPORT", "OBSERVATION", "OUTCOME"]


class FieldSource(WireModel):
    """环境字段的原始来源和确认时刻，客户端不能提交可信来源类型。"""

    source_type: Literal["USER_REPORT", "HUMAN_RECORD", "TICKET_SNAPSHOT"]
    source_id: Identifier
    confirmed_at: AwareDatetime


class TaskEnvironment(SupportEnvironment):
    """任务是环境权威，具名字段旁保存逐字段来源。"""

    schema_version: Literal[1] = 1
    field_sources: dict[EnvironmentField, FieldSource]

    @model_validator(mode="after")
    def attributed_fields(self) -> Self:
        """具体环境必须具有来源；不能给空字段凭空挂上已确认来源。"""
        fields = {
            "os_name",
            "os_version",
            "software_name",
            "software_version",
            "connection_type",
            "affected_scope",
            "error_code",
        }
        expected = {name for name in fields if getattr(self, name) not in {None, "UNKNOWN"}}
        if set(self.field_sources) != expected:
            raise ValueError("环境值与来源字段不匹配")
        return self


class TaskBudget(WireModel):
    """累计模型账目及未知预留的上限，不因新建 Run 清零。"""

    schema_version: Literal[1] = 1
    max_model_calls: int = Field(ge=1, le=100)
    max_tokens: int = Field(ge=1, le=1_000_000)
    max_estimated_cost: Decimal | None = Field(default=None, gt=0, max_digits=20, decimal_places=8)
    currency: Literal["CNY", "USD"]
    policy_version: str = Field(min_length=1, max_length=64)
    unknown_price_policy: Literal["DENY_PAID"] = "DENY_PAID"


class Hypothesis(WireModel):
    """假设不写入事实表，只引用同任务有效事实。"""

    hypothesis_key: str = Field(pattern=r"^[a-z][a-z0-9_]{0,63}$")
    statement: str = Field(min_length=1, max_length=2000)
    status: Literal["PROPOSED", "SUPPORTED", "REFUTED", "INAPPLICABLE"]
    context_version: int = Field(ge=0)
    proposed_run_id: Identifier
    supporting_fact_ids: list[Identifier] = Field(max_length=16)
    opposing_fact_ids: list[Identifier] = Field(max_length=16)

    @model_validator(mode="after")
    def distinct_support(self) -> Self:
        """同一个事实不能同时支持和反证，也不能重复计入依据。"""
        support, opposition = set(self.supporting_fact_ids), set(self.opposing_fact_ids)
        if (
            len(support) != len(self.supporting_fact_ids)
            or len(opposition) != len(self.opposing_fact_ids)
            or support & opposition
        ):
            raise ValueError("假设依据重复或矛盾")
        if not self.statement.strip():
            raise ValueError("假设正文不能为空")
        return self


class HypothesisState(WireModel):
    """当前投影至多八项，归档历史由任务事件负责。"""

    schema_version: Literal[1] = 1
    items: list[Hypothesis] = Field(default_factory=list, max_length=8)

    @model_validator(mode="after")
    def unique_keys(self) -> Self:
        """假设键是任务内身份，不能通过重复条目制造不明确投影。"""
        if len({item.hypothesis_key for item in self.items}) != len(self.items):
            raise ValueError("假设键重复")
        if len(self.model_dump_json().encode("utf-8")) > 32 * 1024:
            raise ValueError("假设投影超过字节预算")
        return self


class AnswerSchema(WireModel):
    """审核规程和宿主支持的四类答案，禁止任意 JSON Schema 或表达式执行。"""

    schema_version: Literal[1] = 1
    kind: Literal["TEXT", "CHOICE", "BOOLEAN", "NUMBER"]
    options: list[Annotated[str, Field(min_length=1, max_length=200)]] = Field(
        default_factory=list,
        max_length=20,
        description="仅CHOICE填写非空且不重复的选项，其他类型必须为空数组",
    )
    minimum: StrictInt | StrictFloat | None = Field(
        default=None, description="仅NUMBER可填写数值下限，TEXT/CHOICE/BOOLEAN必须为null"
    )
    maximum: StrictInt | StrictFloat | None = Field(
        default=None, description="仅NUMBER可填写数值上限，TEXT/CHOICE/BOOLEAN必须为null"
    )
    max_length: int = Field(default=2000, ge=1, le=2000)

    @model_validator(mode="after")
    def shape(self) -> Self:
        """选项、数值范围及文本预算只允许用于对应答案类型。"""
        if (self.kind == "CHOICE") != bool(self.options) or len(set(self.options)) != len(
            self.options
        ):
            raise ValueError("选项与答案类型不匹配")
        if any(not option.strip() for option in self.options):
            raise ValueError("选项不能为空白")
        if self.kind != "NUMBER" and (self.minimum is not None or self.maximum is not None):
            raise ValueError("非数值答案不能配置范围")
        if any(
            isinstance(value, float) and not math.isfinite(value)
            for value in (self.minimum, self.maximum)
        ):
            raise ValueError("数值范围必须有限")
        if self.minimum is not None and self.maximum is not None and self.minimum > self.maximum:
            raise ValueError("最小值不能大于最大值")
        if len(self.model_dump_json().encode("utf-8")) > 8192:
            raise ValueError("答案定义超过字节预算")
        return self

    def validate_value(self, value: str | bool | int | float) -> None:
        """严格保留 false、0 和空缺区别，不把字符串数字当用户数值答案。"""
        if self.kind in {"TEXT", "CHOICE"}:
            if not isinstance(value, str) or not value.strip() or len(value) > self.max_length:
                raise ValueError("需要非空且有界文本答案")
            if self.kind == "CHOICE" and value not in self.options:
                raise ValueError("答案不在审核选项中")
        elif self.kind == "BOOLEAN":
            if not isinstance(value, bool):
                raise ValueError("需要明确的是或否")
        else:
            if (
                isinstance(value, bool)
                or not isinstance(value, (int, float))
                or not math.isfinite(value)
            ):
                raise ValueError("需要有限数值")
            if (self.minimum is not None and value < self.minimum) or (
                self.maximum is not None and value > self.maximum
            ):
                raise ValueError("答案超出规定范围")


class CheckAnswer(WireModel):
    """人工答案只表达用户报告，绝不从已回答推断执行或恢复。"""

    value: StrictStr | StrictBool | StrictInt | StrictFloat
    execution_status: Literal["NOT_PERFORMED", "PERFORMED"] | None = None
    outcome: Literal["UNKNOWN", "UNCHANGED", "IMPROVED", "RESTORED_REPORTED"] | None = None

    @model_validator(mode="after")
    def finite_and_bounded(self) -> Self:
        """拒绝非有限数、非法Unicode与无界值，不能等到SQL或模型边界才失败。"""
        if isinstance(self.value, str):
            if len(self.value) > 2000 or len(self.value.encode("utf-8")) > 8000:
                raise ValueError("答案文本超过预算")
        elif not isinstance(self.value, bool):
            try:
                finite = math.isfinite(self.value)
            except OverflowError:
                finite = False
            if not finite:
                raise ValueError("答案数值必须有限")
        if len(self.model_dump_json().encode("utf-8")) > 8000:
            raise ValueError("答案超过字节预算")
        return self

    def validate_for(self, kind: str, schema: AnswerSchema) -> None:
        """澄清只提交值；检查必须明确执行情况，未执行只能结果未知。"""
        schema.validate_value(self.value)
        if kind == "CLARIFICATION":
            if self.execution_status is not None or self.outcome is not None:
                raise ValueError("澄清不能声明执行情况")
        elif kind == "CHECK":
            if self.execution_status is None or self.outcome is None:
                raise ValueError("检查必须说明是否执行及结果")
            if self.execution_status == "NOT_PERFORMED" and self.outcome != "UNKNOWN":
                raise ValueError("未执行不能报告恢复")
        else:
            raise ValueError("未知检查类型")


class TaskCreate(DiagnosisInput):
    """客户任务固定项目及可选设备，技术任务环境由工单读取。"""

    client_action_id: Identifier
    goal: str = Field(min_length=1, max_length=1000)
    service_item_id: Identifier | None = None
    device_id: Identifier | None = None
    support_context: SupportEnvironment | None = None


class TaskAction(DiagnosisInput):
    """诊断动作的原请求键及预期任务版本。"""

    client_action_id: Identifier
    task_state_version: Version


class TaskFactChange(TaskAction):
    """人工更正链始终追加新事实，不允许覆盖旧来源。"""

    action: Literal["ASSERT", "CORRECT", "RETRACT"]
    statement: str = Field(min_length=1, max_length=2000)
    fact_kind: FactKind = "OBSERVATION"
    predecessor_fact_id: Identifier | None = None
    reported_conditions: list[SafetyCondition] = Field(default_factory=list, max_length=6)

    @model_validator(mode="after")
    def predecessor(self) -> Self:
        """更正/撤回必须指向同任务前驱，所属关系由用例重核。"""
        if (self.action != "ASSERT") != (self.predecessor_fact_id is not None):
            raise ValueError("更正和撤回必须指定前驱")
        if not self.statement.strip():
            raise ValueError("事实说明不能为空白")
        if len(set(self.reported_conditions)) != len(self.reported_conditions) or (
            self.reported_conditions
            and (self.fact_kind != "OBSERVATION" or self.action == "RETRACT")
        ):
            raise ValueError("停止条件只能由非撤回的明确人工观察报告且不可重复")
        return self


class TaskContextChange(TaskAction):
    """技术模式只请求同步，不接受客户端覆盖环境。"""

    support_context: SupportEnvironment | None = None
    service_item_id: Identifier | None = None


class TaskClose(TaskAction):
    """结束任务记录用户原因，不等同客观维修完成。"""

    reason: str = Field(min_length=1, max_length=2000)


class CheckAnswerInput(TaskAction):
    """反馈提交与后续付费 Run 明确分离。"""

    answer: CheckAnswer


class TaskReceipt(WireModel):
    """不可变任务事件中的原提交结果，当前任务另行查询。"""

    task_id: Identifier
    event_id: Identifier
    task_state_version: Version
    status: TaskStatus
    input_message_id: Identifier | None = None
    fact_id: Identifier | None = None
    check_id: Identifier | None = None


class CheckProposal(WireModel):
    """执行器只暂存提议，Run 成功事务才建立正式待答实例。"""

    check_key: str = Field(pattern=r"^[a-z][a-z0-9_]{0,63}$")
    check_kind: Literal["CLARIFICATION", "CHECK"]
    instruction: str = Field(min_length=1, max_length=4000)
    answer_schema: AnswerSchema
    dependency_fields: list[EnvironmentField] = Field(max_length=7)
    procedure_document_id: Identifier | None = None
    procedure_check_key: str | None = Field(default=None, pattern=r"^[a-z][a-z0-9_]{0,63}$")
    previous_check_id: Identifier | None = Field(
        default=None,
        description="仅同一checkKey的旧实例需要复查时填旧checkId；下一种检查不是复查，必须为null",
    )
    source_numbers: list[int] = Field(default_factory=list, max_length=64)

    @model_validator(mode="after")
    def references(self) -> Self:
        """引用及依赖必须明确且无重复，检查建议必须具备可核验依据。"""
        if (self.procedure_document_id is None) != (self.procedure_check_key is None):
            raise ValueError("规程引用须完整")
        if len(set(self.dependency_fields)) != len(self.dependency_fields):
            raise ValueError("环境依赖字段重复")
        if any(number < 1 for number in self.source_numbers) or len(
            set(self.source_numbers)
        ) != len(self.source_numbers):
            raise ValueError("来源号不合法")
        if self.check_kind == "CHECK" and not self.source_numbers:
            raise ValueError("检查指引缺少依据")
        if not self.instruction.strip():
            raise ValueError("检查指引不能为空白")
        return self


class TaskOutcome(TaskClose):
    """明确的人工恢复反馈不等同工具实证，也不关闭正式工单。"""

    restored: Literal[True]


class NormalizedAnswer(WireModel):
    """持久答案与原消息关联，规范值不包含服务端猜测的执行结果。"""

    schema_version: Literal[1] = 1
    answer: CheckAnswer
    source_type: Literal["USER_REPORT", "HUMAN_RECORD"]


class ContextChange(WireModel):
    """逐次环境变化记录；旧检查只有依赖字段未变且版本链完整才仍有效。"""

    previous_version: int = Field(ge=0)
    current_version: int = Field(ge=0)
    changed_fields: list[EnvironmentField] = Field(max_length=7)
    scope_changed: bool
    previous_service_item_id: Identifier | None = None
    current_service_item_id: Identifier | None = None
    previous_device_id: Identifier | None = None
    current_device_id: Identifier | None = None
    previous_product_model_id: Identifier | None = None
    current_product_model_id: Identifier | None = None


class CheckDependencies(WireModel):
    """检查只依赖具名环境字段，不解释模型提供的任意路径或表达式。"""

    schema_version: Literal[1] = 1
    items: list[EnvironmentField] = Field(max_length=7)

    @model_validator(mode="after")
    def unique_fields(self) -> Self:
        """同一字段只计一次，未知字段由Literal拒绝。"""
        if len(set(self.items)) != len(self.items):
            raise ValueError("检查依赖字段重复")
        return self


class TaskEventDetails(WireModel):
    """有界任务事件保留原提交回执和受影响身份，不复制整段聊天。"""

    schema_version: Literal[1] = 1
    receipt: TaskReceipt
    reason: str = Field(min_length=1, max_length=2000)
    changed_check_ids: list[Identifier] = Field(default_factory=list, max_length=2048)
    hypotheses_before: HypothesisState | None = None
    hypotheses_after: HypothesisState | None = None
    context_change: ContextChange | None = None
    draft_root_id: Identifier | None = None
    action_receipt_id: Identifier | None = None
