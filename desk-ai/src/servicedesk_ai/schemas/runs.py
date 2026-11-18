"""单次有界执行的受理输入与不可变上下文清单；不保存隐藏推理。作者：yangaobo。"""

from typing import Literal, Self

from pydantic import Field, model_validator

from servicedesk_ai.bootstrap.components import ExecutionSettings
from servicedesk_ai.schemas.diagnosis import DiagnosisInput, Version
from servicedesk_ai.schemas.indexing import Digest, Identifier, WireModel
from servicedesk_ai.schemas.tasks import (
    CheckProposal,
    EnvironmentField,
    HypothesisState,
    TaskEnvironment,
)


class RunCreate(DiagnosisInput):
    """新消息与已保存反馈二选一；后者不会再次插入用户消息。"""

    client_request_id: Identifier
    task_id: Identifier | None = None
    task_state_version: Version | None = None
    message: str | None = Field(default=None, min_length=1, max_length=12000)
    input_message_id: Identifier | None = None
    resumes_run_id: Identifier | None = None

    @model_validator(mode="after")
    def explicit_input(self) -> Self:
        """任务和预期版本同空同有，空白或超字节输入不能进入持久队列。"""
        if (self.message is None) == (self.input_message_id is None):
            raise ValueError("新消息与已保存输入必须二选一")
        if (self.task_id is None) != (self.task_state_version is None):
            raise ValueError("任务与状态版本必须同时指定")
        if self.message is not None and len(self.message.encode("utf-8")) > 48 * 1024:
            raise ValueError("消息超过字节预算")
        return self


class RunPipeline(ExecutionSettings):
    """受理时固定策略、工具、模型和本轮预算，续行不得改写原运行配置。"""

    schema_version: Literal[1] = 1
    prompt_version: str = Field(min_length=1, max_length=64)
    tool_version: str = Field(min_length=1, max_length=64)
    model: str = Field(min_length=1, max_length=200)


class MessageReference(WireModel):
    """消息不可修改，清单同时固定正文摘要。"""

    id: Identifier
    content_sha256: Digest


class FactReference(WireModel):
    """事实引用固定提交版本，更正追加新ID，不能原地改变已受理输入。"""

    id: Identifier
    state_version: int = Field(ge=1)


class CheckReference(WireModel):
    """检查输入固定行版本与原环境版本。"""

    id: Identifier
    row_version: int = Field(ge=0)
    context_version: int = Field(ge=0)


class ContextSummary(WireModel):
    """摘要只能引用已展开到本轮的根来源，不凭旧摘要获得权限。"""

    content: str = Field(min_length=1, max_length=8000)
    content_sha256: Digest
    through_state_version: int = Field(ge=0)
    context_version: int = Field(ge=0)
    strategy_version: str = Field(min_length=1, max_length=64)
    source_numbers: list[int] = Field(max_length=64)


class ReusedCall(WireModel):
    """只保存明确复用的旧调用身份，完整性和当前权限由恢复用例重核。"""

    call_type: Literal["MODEL", "TOOL"]
    call_id: Identifier
    run_id: Identifier


class HypothesisSuggestion(DiagnosisInput):
    """模型只能提出假设和依据，当前Run及环境版本由宿主填写。"""

    hypothesis_key: str = Field(pattern=r"^[a-z][a-z0-9_]{0,63}$")
    statement: str = Field(min_length=1, max_length=2000)
    status: Literal["PROPOSED", "SUPPORTED", "REFUTED", "INAPPLICABLE"] = Field(
        description="无已提供事实依据时只能PROPOSED；SUPPORTED须有支持factId，REFUTED须有反证factId"
    )
    supporting_fact_ids: list[Identifier] = Field(default_factory=list, max_length=16)
    opposing_fact_ids: list[Identifier] = Field(default_factory=list, max_length=16)

    @model_validator(mode="after")
    def evidence_required(self) -> Self:
        """缺少支持或反证是模型输出错误，不能误报为任务环境变化。"""
        if (self.status == "SUPPORTED" and not self.supporting_fact_ids) or (
            self.status == "REFUTED" and not self.opposing_fact_ids
        ):
            raise ValueError("假设状态缺少对应事实依据；无依据只能提出待验证假设")
        return self


class AnswerProposal(DiagnosisInput):
    """模型提出的引用、假设及待确认建议，不承载宿主确定的业务状态说明。"""

    schema_version: Literal[1] = 1
    citation_numbers: list[int] = Field(default_factory=list, max_length=8)
    hypotheses: list[HypothesisSuggestion] = Field(default_factory=list, max_length=8)
    proposed_record: str | None = Field(default=None, max_length=5000)
    proposed_resolution: str | None = Field(
        default=None,
        max_length=5000,
        description="用户明确请求整理工单解决草稿时才填写，并同时填写resolutionType；普通交接回答为null",
    )
    resolution_type: Literal["RESTORED", "GUIDANCE", "REFERRED"] | None = Field(
        default=None,
        description="仅proposedResolution非空时填写，否则必须为null；不能用此字段代替answerType",
    )
    referral_target: str | None = Field(
        default=None,
        max_length=300,
        description="仅proposedResolution非空且resolutionType为REFERRED时必填，其余情况必须为null",
    )

    @model_validator(mode="after")
    def distinct_proposals(self) -> Self:
        """唯一身份和有界UTF-8正文在模型边界校验，正式引用由完成用例再核验。"""
        if len(set(self.citation_numbers)) != len(self.citation_numbers) or any(
            number < 1 for number in self.citation_numbers
        ):
            raise ValueError("引用编号重复或非法")
        if len({item.hypothesis_key for item in self.hypotheses}) != len(self.hypotheses):
            raise ValueError("本轮假设键重复")
        return self


class AnswerContent(AnswerProposal):
    """普通知识回答和咨询的自由正文，交付前按相应模式验证。"""

    text: str = Field(min_length=1, max_length=8000)

    @model_validator(mode="after")
    def bounded_text(self) -> Self:
        """公开正文的字节边界独立于审核规程决策。"""
        if len(self.text.encode("utf-8")) > 48 * 1024:
            raise ValueError("回答超过字节限制")
        return self


class RunResult(AnswerContent):
    """最终持久及浏览器合同；检查指令完整，不接受模型宣告正式业务成功。"""

    answer_type: Literal["ANSWERED", "CLARIFICATION", "INSUFFICIENT_EVIDENCE", "HANDOVER"] = Field(
        description=(
            "ANSWERED为有依据的回答或检查建议，也用于引用用户复测反馈说明恢复；"
            "CLARIFICATION仅补问必要信息；INSUFFICIENT_EVIDENCE表示证据不足；"
            "HANDOVER仅表示仍有未解决问题或风险需要人工接手，不表示正常结束对话"
        )
    )
    checks: list[CheckProposal] = Field(default_factory=list, max_length=5)

    @model_validator(mode="after")
    def distinct_checks(self) -> Self:
        """一个提交不能生成重复检查键。"""
        if len({item.check_key for item in self.checks}) != len(self.checks):
            raise ValueError("本轮检查键重复")
        return self


class ProcedureSelection(DiagnosisInput):
    """模型只选择本轮读过的审核检查，指令和选项由宿主从不可变快照取得。"""

    source_number: int = Field(ge=1, description="本轮load_procedure的对应检查来源号")


class ProcedureClarification(DiagnosisInput):
    """规程缺少必要输入时只提出文本澄清，不能借此生成任意操作。"""

    kind: Literal["CLARIFY"]
    check_key: str = Field(pattern=r"^[a-z][a-z0-9_]{0,63}$")
    question: str = Field(min_length=1, max_length=1000)
    dependency_fields: list[EnvironmentField] = Field(default_factory=list, max_length=7)
    previous_check_id: Identifier | None = None


class ProcedureChecks(DiagnosisInput):
    """选择审核检查并等待反馈，与补充环境信息及结束本轮互斥。"""

    kind: Literal["CHECKS"]
    selections: list[ProcedureSelection] = Field(min_length=1, max_length=5)

    @model_validator(mode="after")
    def consistent_selection(self) -> Self:
        """同一来源只能选一次。"""
        numbers = [item.source_number for item in self.selections]
        if len(set(numbers)) != len(numbers):
            raise ValueError("审核检查来源重复")
        return self


class ProcedureConclusion(DiagnosisInput):
    """本轮无需新检查或澄清时给出结论，不能将正常结束误标为人工交接。"""

    kind: Literal["CONCLUDE"]
    answer_type: Literal["ANSWERED", "INSUFFICIENT_EVIDENCE", "HANDOVER"] = Field(
        description=(
            "仅引用当前人工复测恢复反馈用ANSWERED；缺少充分证据用INSUFFICIENT_EVIDENCE；"
            "仍有未解决问题或风险需要人工接手才用HANDOVER。正常结束不是HANDOVER"
        )
    )
    feedback_source_number: int | None = Field(
        default=None,
        ge=1,
        description="ANSWERED必须选择当前用户报告恢复的MESSAGE来源号，其余为null",
    )

    @model_validator(mode="after")
    def recovery_reference(self) -> Self:
        """普通完成操作不能代替复测恢复，恢复结论须显式选择反馈证据。"""
        if (self.answer_type == "ANSWERED") != (self.feedback_source_number is not None):
            raise ValueError("规程恢复结论必须且只能绑定人工复测反馈来源")
        return self


class ProcedureResult(AnswerProposal):
    """模型选择下一步及可选分析，宿主依据审核检查和人工反馈生成状态说明。"""

    next_action: ProcedureChecks | ProcedureClarification | ProcedureConclusion = Field(
        description="只选一种：CHECKS选择已读审核检查；CLARIFY补问必要信息；CONCLUDE给出本轮结论"
    )


class TicketSnapshot(WireModel):
    """受理时固定的正式工单用户描述，版本与事件游标不互换。"""

    title: str = Field(max_length=100)
    symptom: str = Field(max_length=5000)
    attempted_actions: str | None = Field(default=None, max_length=5000)
    ticket_row_version: int = Field(ge=0)
    ticket_event_sequence: int = Field(ge=0)


class RunContext(WireModel):
    """输入范围与清单均固定，后续任务变更不回写旧Run快照。"""

    schema_version: Literal[1] = 1
    mode: Literal["CONSULT", "CUSTOMER_SELF_SERVICE", "TECHNICIAN_ASSIST"]
    customer_id: Identifier | None
    service_item_id: Identifier | None
    device_id: Identifier | None
    product_model_id: Identifier | None
    ticket_id: Identifier | None
    support_context: TaskEnvironment | None
    goal: str | None = Field(default=None, min_length=1, max_length=1000)
    ticket_snapshot: TicketSnapshot | None = None
    messages: list[MessageReference] = Field(max_length=64)
    facts: list[FactReference] = Field(default_factory=list, max_length=64)
    checks: list[CheckReference] = Field(default_factory=list, max_length=64)
    source_usages: list[Identifier] = Field(default_factory=list, max_length=64)
    hypotheses: HypothesisState = Field(default_factory=HypothesisState)
    summary: ContextSummary | None = None
    reused_calls: list[ReusedCall] = Field(default_factory=list, max_length=32)
    missing_context: list[str] = Field(default_factory=list, max_length=16)

    @model_validator(mode="after")
    def bounded_scope(self) -> Self:
        """咨询不能暗含业务绑定；清单禁止重复身份或无界JSON。"""
        if self.mode == "CONSULT" and any(
            (
                self.ticket_id,
                self.service_item_id,
                self.device_id,
                self.product_model_id,
                self.support_context,
                self.ticket_snapshot,
                self.goal,
                self.facts,
                self.checks,
                self.source_usages,
                self.hypotheses.items,
            )
        ):
            raise ValueError("咨询不能带诊断范围或来源")
        if self.mode != "CONSULT" and (self.customer_id is None or self.support_context is None):
            raise ValueError("诊断运行缺少客户及来源化环境")
        if (self.mode == "TECHNICIAN_ASSIST") != (self.ticket_id is not None):
            raise ValueError("工单绑定与会话模式不匹配")
        for items in (self.messages, self.facts, self.checks):
            if len({item.id for item in items}) != len(items):
                raise ValueError("输入清单身份重复")
        if len(set(self.source_usages)) != len(self.source_usages):
            raise ValueError("实际来源身份重复")
        if len(self.model_dump_json().encode("utf-8")) > 64 * 1024:
            raise ValueError("上下文清单超过字节预算")
        return self
