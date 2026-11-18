"""人工规程的有限合同；模型不能增加风险权限、执行脚本或改写审核定义。作者：yangaobo。"""

from typing import Annotated, Literal, Self

from pydantic import Field, model_validator

from servicedesk_ai.schemas.indexing import Digest, Identifier, WireModel
from servicedesk_ai.schemas.tasks import AnswerSchema, EnvironmentField, SafetyCondition

Condition = SafetyCondition
CheckKey = Annotated[str, Field(pattern=r"^[a-z][a-z0-9_]{0,63}$")]


class ProcedureLocator(WireModel):
    """审核时已经核对的原件物理页或真实行范围。"""

    kind: Literal["PDF", "MARKDOWN"]
    page_number: int | None = Field(default=None, ge=1)
    anchor: str | None = Field(default=None, min_length=1, max_length=200)
    line_start: int | None = Field(default=None, ge=1)
    line_end: int | None = Field(default=None, ge=1)

    @model_validator(mode="after")
    def shape(self) -> Self:
        """页与行互斥，不接受格式错配定位。"""
        if self.kind == "PDF":
            if self.page_number is None or any(
                v is not None for v in (self.anchor, self.line_start, self.line_end)
            ):
                raise ValueError("PDF定位错误")
        elif (
            self.page_number is not None
            or self.anchor is None
            or self.line_start is None
            or self.line_end is None
            or self.line_end < self.line_start
        ):
            raise ValueError("Markdown定位错误")
        return self


class PrerequisiteAnswer(WireModel):
    """前置CHOICE检查必须返回的允许值；只做集合匹配，不解释表达式。"""

    check_key: CheckKey
    allowed_values: list[Annotated[str, Field(min_length=1, max_length=200)]] = Field(
        min_length=1, max_length=20
    )

    @model_validator(mode="after")
    def distinct_values(self) -> Self:
        """不允许重复或空白值扩大人工审核条件。"""
        if len(set(self.allowed_values)) != len(self.allowed_values) or any(
            not value.strip() for value in self.allowed_values
        ):
            raise ValueError("前置答案值重复或为空")
        return self


class ProcedureCheck(WireModel):
    """人工确认的单项检查，前置键只可指向本定义较早检查。"""

    check_key: CheckKey
    roles: list[Literal["CUSTOMER", "TECHNICIAN"]] = Field(min_length=1, max_length=2)
    instruction: str = Field(min_length=1, max_length=4000)
    answer_schema: AnswerSchema
    required_fields: list[EnvironmentField] = Field(max_length=7)
    prerequisite_check_keys: list[CheckKey] = Field(max_length=20)
    prerequisite_answers: list[PrerequisiteAnswer] = Field(default_factory=list, max_length=20)
    risk_level: Literal["LOW", "HANDOVER_REQUIRED"]
    stop_conditions: list[Condition] = Field(max_length=10)
    source_locator: ProcedureLocator

    @model_validator(mode="after")
    def answer_dependencies(self) -> Self:
        """值条件必须属于本检查声明的前置，不接受隐含或重复的依赖。"""
        keys = [condition.check_key for condition in self.prerequisite_answers]
        if len(set(keys)) != len(keys) or not set(keys).issubset(self.prerequisite_check_keys):
            raise ValueError("前置答案引用重复或未声明")
        return self


class ProcedureDefinition(WireModel):
    """只读规程可供宿主选取下一项，不构成任意流程引擎。"""

    schema_version: Literal[1]
    applicability: str = Field(min_length=1, max_length=2000)
    required_fields: list[EnvironmentField] = Field(max_length=7)
    checks: list[ProcedureCheck] = Field(min_length=1, max_length=20)
    stop_conditions: list[Condition] = Field(max_length=10)
    handover_conditions: list[Condition] = Field(max_length=10)

    @model_validator(mode="after")
    def ordered(self) -> Self:
        """提前拒绝循环、重复键或将交接操作授予客户的定义。"""
        seen: dict[str, ProcedureCheck] = {}
        for check in self.checks:
            if check.check_key in seen or not set(check.prerequisite_check_keys).issubset(seen):
                raise ValueError("规程检查引用错误")
            if check.risk_level == "HANDOVER_REQUIRED" and "CUSTOMER" in check.roles:
                raise ValueError("客户不能执行交接操作")
            for condition in check.prerequisite_answers:
                prior = seen[condition.check_key]
                if prior.answer_schema.kind != "CHOICE" or not set(
                    condition.allowed_values
                ).issubset(prior.answer_schema.options):
                    raise ValueError("前置答案必须来自较早CHOICE检查的审核选项")
            seen[check.check_key] = check
        if len(self.model_dump_json().encode()) > 65536:
            raise ValueError("规程超过预算")
        return self


class ReadProcedure(WireModel):
    """文档和检查键必须已由授权召回返回，不能任意枚举。"""

    document_id: Identifier
    check_key: CheckKey


class ProcedureSnapshot(WireModel):
    """一项已读检查及定义全局约束，供持久来源与人工引用展示共同使用。"""

    schema_version: Literal[1]
    definition_sha256: Digest
    applicability: str
    required_fields: list[EnvironmentField]
    stop_conditions: list[Condition]
    handover_conditions: list[Condition]
    check: ProcedureCheck
