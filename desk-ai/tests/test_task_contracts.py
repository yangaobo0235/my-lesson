"""诊断反馈的来源、真实执行语义与字节预算回归。作者：yangaobo。"""

import pytest
from pydantic import ValidationError

from servicedesk_ai.schemas.tasks import (
    AnswerSchema,
    CheckAnswer,
    Hypothesis,
    HypothesisState,
    TaskClose,
    TaskEnvironment,
    TaskFactChange,
)
from servicedesk_ai.services.tasks import invalidate_hypotheses


def test_reported_stop_conditions_are_explicit_and_cannot_be_retracted_positive():
    """只有明确人工观察可以标记停止；撤回不能同时新增阳性报告。"""
    form = dict(
        client_action_id="a" * 32,
        task_state_version="1",
        action="ASSERT",
        statement="需要管理员确认",
        fact_kind="OBSERVATION",
        reported_conditions=["ADMIN_PRIVILEGE_REQUIRED"],
    )
    assert TaskFactChange.model_validate(form).reported_conditions == ["ADMIN_PRIVILEGE_REQUIRED"]
    for change in (
        {"reported_conditions": ["EXECUTE_SHELL"]},
        {"reported_conditions": ["ADMIN_PRIVILEGE_REQUIRED"] * 2},
        {"fact_kind": "ACTION_REPORT"},
        {"action": "RETRACT", "predecessor_fact_id": "b" * 32},
    ):
        with pytest.raises(ValidationError):
            TaskFactChange.model_validate(form | change)


@pytest.mark.parametrize("value", [False, 0])
def test_feedback_preserves_false_and_zero(value):
    """false与0都是明确答案，不能被视为未填写或相互强转。"""
    answer = CheckAnswer(value=value, execution_status="NOT_PERFORMED", outcome="UNKNOWN")
    schema = AnswerSchema(kind="BOOLEAN" if isinstance(value, bool) else "NUMBER")
    answer.validate_for("CHECK", schema)
    assert type(answer.value) is type(value)
    assert answer.value == value


def test_answer_does_not_prove_performed_or_restored():
    """人工反馈不能通过回答成功、缺省字段或NOT_PERFORMED宣称已恢复。"""
    schema = AnswerSchema(kind="BOOLEAN")
    with pytest.raises(ValueError):
        CheckAnswer(value=True).validate_for("CHECK", schema)
    with pytest.raises(ValueError):
        CheckAnswer(
            value=True, execution_status="NOT_PERFORMED", outcome="RESTORED_REPORTED"
        ).validate_for("CHECK", schema)
    with pytest.raises(ValueError):
        CheckAnswer(value=True, execution_status="PERFORMED", outcome="UNCHANGED").validate_for(
            "CLARIFICATION", schema
        )


@pytest.mark.parametrize("value", [float("nan"), float("inf"), 10**400, "\ud800", "😀" * 2001])
def test_invalid_external_answer_rejected_before_persistence(value):
    """非有限数、非法UTF-8和大正文都是输入拒绝，不能进入账目或数据库。"""
    with pytest.raises((ValidationError, ValueError)):
        CheckAnswer(value=value)


def test_numeric_answers_are_strict():
    """布尔或字符串不能冒充数值，显式范围也必须成立。"""
    schema = AnswerSchema(kind="NUMBER", minimum=0, maximum=10)
    for value in (False, "2", -1, 11):
        with pytest.raises(ValueError):
            schema.validate_value(value)
    schema.validate_value(0)


def test_environment_cannot_claim_unsourced_values():
    """所有具体字段逐项有来源，拒绝只挂一个全局确认标记。"""
    with pytest.raises(ValidationError):
        TaskEnvironment(os_name="Windows", field_sources={})
    assert TaskEnvironment(field_sources={}).os_name is None


def test_task_close_requires_nonblank_reason():
    """终态必须保留明确人工原因，不接受空白充当说明。"""
    with pytest.raises(ValidationError):
        TaskClose(client_action_id="a" * 32, task_state_version="1", reason=" \n ")


def test_fact_correction_invalidates_only_related_hypothesis():
    """更正失效受影响推断，不能改写原事实或让无关推断假装重新确认。"""
    one = Hypothesis(
        hypothesis_key="vpn_route",
        statement="可能路由冲突",
        status="SUPPORTED",
        context_version=1,
        proposed_run_id="a" * 32,
        supporting_fact_ids=["b" * 32],
        opposing_fact_ids=[],
    )
    two = one.model_copy(
        update={"hypothesis_key": "printer_state", "supporting_fact_ids": ["c" * 32]}
    )
    original = HypothesisState(items=[one, two])
    changed = invalidate_hypotheses(original, "b" * 32)
    assert changed.items[0].status == "INAPPLICABLE"
    assert changed.items[1].status == "SUPPORTED"
    assert original.items[0].status == "SUPPORTED"
    assert all(item.status == "INAPPLICABLE" for item in invalidate_hypotheses(original).items)
