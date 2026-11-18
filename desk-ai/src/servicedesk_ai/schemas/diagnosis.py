"""私有会话、版本化事实、生成、草稿及反馈的完整输入与公开投影。作者：yangaobo。"""

from typing import Annotated, Literal, Self

from pydantic import AfterValidator, AwareDatetime, Field, model_validator

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
    """会话只固定模式和私有目标；诊断范围与环境归任务。"""

    mode: Literal["CONSULT", "CUSTOMER_SELF_SERVICE", "TECHNICIAN_ASSIST"]
    title: str = Field(min_length=1, max_length=100)
    client_request_id: Identifier
    ticket_id: Identifier | None = None

    @model_validator(mode="after")
    def fixed_binding(self) -> Self:
        """仅技术模式允许且必须固定工单。"""
        if (self.mode == "TECHNICIAN_ASSIST") != (self.ticket_id is not None):
            raise ValueError("会话模式与工单绑定不匹配")
        return self


class ConversationChange(DiagnosisInput):
    """改名与删除均比较会话管理版本。"""

    row_version: Version
    title: str | None = Field(default=None, min_length=1, max_length=100)


class DraftContent(WireModel):
    """草稿待人编辑，建议与确认执行分开，不增加模型调用。"""

    schema_version: Literal[1] = 1
    title: str = ""
    symptom: str = ""
    attempted_actions: str = ""
    content: str = ""
    resolution_type: Literal["RESTORED", "GUIDANCE", "REFERRED"] | None = None
    referral_target: str | None = None
    follow_up_at: AwareDatetime | None = Field(default=None, exclude_if=lambda value: value is None)
    slot_id: Identifier | None = Field(default=None, exclude_if=lambda value: value is None)


DraftPurpose = Literal[
    "CREATE_TICKET", "ADD_RECORD", "RESOLVE_TICKET", "REQUEST_INFORMATION", "BOOK_ONSITE"
]


class DraftCreate(WireModel):
    """创建草稿不发模型请求，客户仅采用明确确认的本人反馈。"""

    source_run_id: Identifier | None = None
    purpose: DraftPurpose
    client_request_id: Identifier
    confirmed_fact_ids: list[Identifier] = Field(default_factory=list, max_length=30)
    follow_up_at: AwareDatetime | None = Field(default=None, exclude_if=lambda value: value is None)
    slot_id: Identifier | None = Field(default=None, exclude_if=lambda value: value is None)
    wait_for_confirmation: bool = Field(default=False, exclude_if=lambda value: value is False)

    @model_validator(mode="after")
    def purpose_fields(self) -> Self:
        """具体时间和时段由人选择，不允许无关用途夹带动作参数。"""
        if (self.purpose == "REQUEST_INFORMATION") != (self.follow_up_at is not None):
            raise ValueError("补充请求须指定跟进时刻")
        if (self.purpose == "BOOK_ONSITE") != (self.slot_id is not None):
            raise ValueError("预约须指定时段")
        if self.purpose == "BOOK_ONSITE" and (self.source_run_id or self.wait_for_confirmation):
            raise ValueError("已交接任务的预约不依赖模型运行或重开诊断")
        return self


class FeedbackInput(DiagnosisInput):
    """本人对终态运行的评价和实际采纳反馈，不授予聊天访问权。"""

    rating: Literal["HELPFUL", "UNHELPFUL"]
    adoption: Literal["NOT_USED", "ACCEPTED", "MODIFIED", "REJECTED"]
    issue_category: (
        Literal[
            "INCORRECT",
            "IRRELEVANT",
            "MISSING_CONTEXT",
            "UNSUPPORTED",
            "OTHER",
        ]
        | None
    ) = None
    comment: str | None = Field(default=None, max_length=2000)
    row_version: Version | None = None

    @model_validator(mode="after")
    def category_required(self) -> Self:
        """只有无帮助反馈需要分类。"""
        if (self.rating == "UNHELPFUL") != (self.issue_category is not None):
            raise ValueError("反馈分类与评价不匹配")
        return self


class SearchKnowledge(DiagnosisInput):
    """工具范围由宿主固定，只允许模型选择查询文本和有界数量。"""

    query: str = Field(min_length=1, max_length=1000)
    document_title: str | None = Field(
        default=None,
        min_length=1,
        max_length=300,
        description="用户指定完整资料标题时原样填写，只搜索当前有权且标题完全一致的文档；未指定时为null",
    )
    source_type: Literal["OFFICIAL_MANUAL", "TROUBLESHOOTING_GUIDE", "SANITIZED_CASE"] | None = (
        Field(
            default=None,
            description="默认不筛选；仅用户明确要求知识分类时指定。厂商发布的排障指南也属于TROUBLESHOOTING_GUIDE，官方来源不等于OFFICIAL_MANUAL。",
        )
    )
    limit: int = Field(default=8, ge=1, le=8)


class ReadEvidence(WireModel):
    """按候选与字符游标阅读，完整章节可由多个有界正文页组成。"""

    chunk_id: Identifier
    expand_section: bool = Field(default=True, description="将候选叶片扩展到父章节时必须从0开始")
    offset: int = Field(
        default=0,
        ge=0,
        le=49152,
        strict=True,
        description="首次阅读为0；续页仅使用正文回执的chunkId与nextOffset，不能沿用搜索预览的endOffset",
    )


class ReadTaskContext(DiagnosisInput):
    """仅查询当前私有任务；字面子串筛选与ID游标不允许指定其他任务或客户。"""

    section: Literal["FACTS", "CHECKS"]
    before_id: Identifier | None = None
    query: str | None = Field(default=None, min_length=1, max_length=100)
    limit: int = Field(default=3, ge=1, le=3)


class ListDiagnosticAttachments(WireModel):
    """固定工单的可读脱敏附件游标，不接受外部URL或客户范围。"""

    after_id: Identifier | None = None


class InspectDiagnosticText(WireModel):
    """固定脱敏版本按Unicode字符分页，不执行正文或跟随正文中的链接。"""

    attachment_id: Identifier
    redaction_version: Version
    offset: int = Field(default=0, ge=0, le=1048576, strict=True)

    @model_validator(mode="after")
    def confirmed_version(self) -> Self:
        """未确认版本不能交给诊断工具读取。"""
        if int(self.redaction_version) < 1:
            raise ValueError("附件必须是已确认的脱敏版本")
        return self


class ReadTicketEvents(WireModel):
    """工单固定在会话，游标与ID集合二选一。"""

    after_sequence: int | None = Field(default=None, ge=0, le=9223372036854775807)
    event_ids: list[Identifier] = Field(default_factory=list, max_length=20)
    limit: int = Field(default=20, ge=1, le=20)

    @model_validator(mode="after")
    def one_selector(self) -> Self:
        """避免不明确的事件读取语义。"""
        if self.event_ids and self.after_sequence is not None:
            raise ValueError("事件ID与游标二选一")
        return self


class DraftRevision(WireModel):
    """编辑必须针对原修订及其摘要，未知响应保留原请求键。"""

    client_request_id: Identifier
    content_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    content: DraftContent


class DraftWithdraw(WireModel):
    """撤回仅阻止后续校验，不声明在途正式动作已取消。"""

    content_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
