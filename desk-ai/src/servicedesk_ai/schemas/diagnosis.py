"""私有会话、版本化事实、生成、草稿及反馈的完整输入与公开投影。作者：yangaobo。"""

from typing import Annotated, Literal, Self

from pydantic import AfterValidator, Field, model_validator

from servicedesk_ai.schemas.indexing import Identifier, WireModel


def bounded_version(value: str) -> str:
    """跨端版本按Java有符号long范围传输，避免数据库隐式溢出。"""
    if int(value) > 9223372036854775807:
        raise ValueError("版本超出范围")
    return value


Version = Annotated[str, Field(pattern=r"^(0|[1-9]\d{0,18})$"), AfterValidator(bounded_version)]


class DiagnosisInput(WireModel):
    """公开文本拒绝纯空白，不修改用户原始输入或幂等摘要。"""

    @model_validator(mode="after")
    def nonblank_text(self) -> Self:
        """所有直接文本字段统一检查；有限枚举和标识仍由各字段校验。"""
        for name in type(self).model_fields:
            value = getattr(self, name)
            if isinstance(value, str) and not value.strip():
                raise ValueError("文本不能为空白")
        return self


class SupportEnvironment(DiagnosisInput):
    """未知保持空值，名称和版本相互约束，不允许客户端声明遥测来源。"""

    os_name: str | None = Field(default=None, min_length=1, max_length=100)
    os_version: str | None = Field(default=None, min_length=1, max_length=100)
    software_name: str | None = Field(default=None, min_length=1, max_length=100)
    software_version: str | None = Field(default=None, min_length=1, max_length=100)
    connection_type: Literal["USB", "LAN", "WIFI", "VPN", "OTHER", "UNKNOWN"] = "UNKNOWN"
    affected_scope: Literal["SINGLE_USER", "MULTIPLE_USERS", "UNKNOWN"] = "UNKNOWN"
    error_code: str | None = Field(default=None, min_length=1, max_length=100)

    @model_validator(mode="after")
    def names_required(self) -> Self:
        """版本存在时名称必填，未知值不转换为通配。"""
        if (self.os_version and not self.os_name) or (
            self.software_version and not self.software_name
        ):
            raise ValueError("版本必须有对应名称")
        return self


class ConversationCreate(DiagnosisInput):
    """技术模式只允许固定工单，客户模式固定项目及可选设备。"""

    mode: Literal["CUSTOMER_SELF_SERVICE", "TECHNICIAN_ASSIST"]
    title: str = Field(min_length=1, max_length=100)
    service_item_id: Identifier | None = None
    device_id: Identifier | None = None
    ticket_id: Identifier | None = None
    support_context: SupportEnvironment | None = None

    @model_validator(mode="after")
    def fixed_binding(self) -> Self:
        """浏览器不能覆盖技术工单的项目、资产或环境。"""
        if self.mode == "TECHNICIAN_ASSIST":
            if not self.ticket_id or self.service_item_id or self.device_id or self.support_context:
                raise ValueError("技术会话仅接受工单绑定")
        elif not self.service_item_id or self.ticket_id or self.support_context is None:
            raise ValueError("客户会话缺少支持上下文")
        return self


class ConversationChange(DiagnosisInput):
    """改名与删除均比较会话管理版本。"""

    row_version: Version
    title: str | None = Field(default=None, min_length=1, max_length=100)


class GenerationCreate(DiagnosisInput):
    """同拥有者原键先返回，只有新受理才校验事实版本和额度。"""

    question: str = Field(min_length=1, max_length=4000)
    client_request_id: Identifier
    state_version: Version


class FactChange(DiagnosisInput):
    """客户端只能追加当前操作者反馈，来源类型由服务端生成。"""

    state_version: Version
    client_action_id: Identifier
    action: Literal["ASSERT", "CORRECT", "RETRACT", "SET_CONTEXT"]
    statement: str | None = Field(default=None, min_length=1, max_length=2000)
    fact_kind: Literal["OBSERVATION", "PERFORMED_ACTION"] = "OBSERVATION"
    fact_id: Identifier | None = None
    support_context: SupportEnvironment | None = None

    @model_validator(mode="after")
    def action_fields(self) -> Self:
        """动作输入互斥，撤回明确前驱与原因，不静默覆盖。"""
        if self.action == "SET_CONTEXT":
            if self.support_context is None or self.fact_id or self.statement:
                raise ValueError("环境动作只接受完整环境")
        elif not self.statement or self.support_context is not None:
            raise ValueError("反馈动作需要正文")
        if (self.action in {"CORRECT", "RETRACT"}) != (self.fact_id is not None):
            raise ValueError("更正与撤回必须指明前驱")
        return self


class DraftCreate(WireModel):
    """创建草稿不发模型请求，客户仅采用明确确认的本人反馈。"""

    source_generation_id: Identifier
    purpose: Literal["CREATE_TICKET", "ADD_RECORD", "RESOLVE_TICKET"]
    client_request_id: Identifier
    confirmed_fact_ids: list[Identifier] = Field(default_factory=list, max_length=30)


class FeedbackInput(DiagnosisInput):
    """默认不共享；共享由单独的版本化动作处理。"""

    rating: Literal["HELPFUL", "UNHELPFUL"]
    issue_category: (
        Literal[
            "INCORRECT_ANSWER",
            "MISSING_CONDITION",
            "INCORRECT_CITATION",
            "OUTDATED_SOURCE",
            "OTHER",
        ]
        | None
    ) = None
    comment: str | None = Field(default=None, max_length=1000)

    @model_validator(mode="after")
    def category_required(self) -> Self:
        """只有无帮助反馈需要分类。"""
        if (self.rating == "UNHELPFUL") != (self.issue_category is not None):
            raise ValueError("反馈分类与评价不匹配")
        return self


class FeedbackShare(WireModel):
    """技术会话不支持共享；撤销共享保留已有处理结论。"""

    row_version: Version
    share: bool


class FeedbackHandle(DiagnosisInput):
    """处置比较反馈版本，保留人工确认的处理说明。"""

    row_version: Version
    action: Literal["RESOLVE", "DISMISS"]
    handling_note: str = Field(min_length=1, max_length=2000)


class DiagnosisResult(DiagnosisInput):
    """模型只能返回公开结果与允许编号，不返回或保存隐藏思维链。"""

    schema_version: Literal[1] = 1
    answer_type: Literal["ANSWERED", "CLARIFICATION", "INSUFFICIENT_EVIDENCE"]
    text: str = Field(min_length=1, max_length=8000)
    citation_numbers: list[int] = Field(default_factory=list, max_length=8)
    open_questions: list[Annotated[str, Field(min_length=1, max_length=500)]] = Field(
        default_factory=list, max_length=5
    )
    hypotheses: list[Annotated[str, Field(min_length=1, max_length=500)]] = Field(
        default_factory=list, max_length=5
    )
    proposed_record: str | None = Field(default=None, max_length=5000)
    proposed_resolution: str | None = Field(default=None, max_length=5000)
    resolution_type: Literal["RESTORED", "GUIDANCE", "REFERRED"] | None = None
    referral_target: str | None = Field(default=None, max_length=500)


class SearchKnowledge(DiagnosisInput):
    """工具范围由宿主固定，只允许模型选择查询文本和有界数量。"""

    query: str = Field(min_length=1, max_length=1000)
    source_type: Literal["OFFICIAL_MANUAL", "TROUBLESHOOTING_GUIDE", "SANITIZED_CASE"] | None = None
    limit: int = Field(default=8, ge=1, le=8)


class ReadKnowledge(WireModel):
    """只能读取本轮已知候选，不能由模型指定任意任务。"""

    chunk_ids: list[Identifier] = Field(min_length=1, max_length=8)


class ReadTicketEvents(WireModel):
    """工单固定在会话，游标与ID集合二选一。"""

    after_sequence: int | None = Field(default=None, ge=0)
    event_ids: list[Identifier] = Field(default_factory=list, max_length=20)
    limit: int = Field(default=20, ge=1, le=20)

    @model_validator(mode="after")
    def one_selector(self) -> Self:
        """避免不明确的事件读取语义。"""
        if self.event_ids and self.after_sequence is not None:
            raise ValueError("事件ID与游标二选一")
        return self
