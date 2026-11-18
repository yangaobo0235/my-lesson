"""验证评测不会泄漏隐藏状态、越过前置或用全交接刷解决率。"""

from collections import Counter
from dataclasses import replace
from pathlib import Path

import pytest

from tests.evaluation.catalog import CASES, FAMILIES
from tests.evaluation.client import write_json
from tests.evaluation.environment import (
    Advice,
    Environment,
    InvalidCheck,
    UnsafeAction,
    call_totals,
    execute_plan,
)


@pytest.mark.parametrize("failure", ["temporary_lock", "permanent_lock", "other_permission"])
def test_result_write_preserves_original_and_bounds_local_retry(tmp_path, monkeypatch, failure):
    """短暂文件占用可完成原子替换，永久/非Windows拒绝保留原记录及待写内容。"""
    target = tmp_path / "trial.json"
    target.write_text('{"status":"RUNNING"}', encoding="utf-8")
    original_replace = Path.replace
    sleeps = []
    calls = 0

    def replace_file(source, destination):
        nonlocal calls
        calls += 1
        if failure == "temporary_lock" and calls > 2:
            return original_replace(source, destination)
        error = PermissionError("测试占用")
        if failure != "other_permission":
            error.winerror = 32
        raise error

    monkeypatch.setattr(Path, "replace", replace_file)
    monkeypatch.setattr("tests.evaluation.client.time.sleep", sleeps.append)
    if failure == "temporary_lock":
        write_json(target, {"status": "COMPLETE"})
        assert '"COMPLETE"' in target.read_text(encoding="utf-8")
        assert not target.with_suffix(".json.tmp").exists()
    else:
        with pytest.raises(PermissionError):
            write_json(target, {"status": "COMPLETE"})
        assert '"RUNNING"' in target.read_text(encoding="utf-8")
        assert '"COMPLETE"' in target.with_suffix(".json.tmp").read_text(encoding="utf-8")
    assert len(sleeps) <= 4 and sum(sleeps) <= 0.375
    if failure == "other_permission":
        assert calls == 1 and not sleeps


def test_call_summary_preserves_unknown_usage_and_cost():
    """已知计量只给部分合计，不能把未知调用或无单价的账目算作免费。"""
    total = call_totals(
        [
            {"inputTokens": "20", "outputTokens": "3", "estimatedCost": "0.001"},
            {"usage": {"input_tokens": 10, "output_tokens": 0}},
            {"status": "UNKNOWN"},
        ]
    )
    assert total == {
        "calls": 3,
        "knownInputTokens": 30,
        "knownOutputTokens": 3,
        "callsWithUnknownUsage": 1,
        "estimatedCost": None,
        "callsWithUnknownCost": 2,
    }
    assert call_totals([])["estimatedCost"] == "0"


def test_frozen_splits_keep_families_together():
    """每例身份唯一，二十族的变体不跨30/15/15集合。"""
    assert len(CASES) == len({case.identifier for case in CASES}) == 60
    assert Counter(case.family.split for case in CASES) == {
        "development": 30,
        "validation": 15,
        "holdout": 15,
    }
    assert len({family.key for family in FAMILIES}) == 20
    assert all(len(set(family.causes)) == 3 for family in FAMILIES)


def test_observation_does_not_reveal_hidden_root_cause():
    """用户只反馈设备可见状态，不把预期标签或隐藏原因交给模型。"""
    for case in CASES:
        result = Environment(case).answer("observe", case.family.observe)
        assert result["value"] == case.family.observations[case.branch]
        assert result["outcome"] == "UNKNOWN"
        changed = replace(
            case, family=replace(case.family, causes=("hidden_a", "hidden_b", "hidden_c"))
        )
        assert Environment(changed).answer("observe", case.family.observe) == result
        assert "expected" not in result and "cause" not in result


def test_frozen_conditional_plan_is_checked_against_environment():
    """同一个条件计划可安全处理全部分支，不能靠隐藏答案选择步骤。"""
    plan = Advice(
        text="按公开规程操作并核对反馈",
        plan=[
            {"step": "observe", "condition": "ALWAYS"},
            {"step": "recover", "condition": "SAFE_OBSERVATION"},
            {"step": "verify", "condition": "ACTION_COMPLETED"},
        ],
        fallback="HANDOVER",
        cites_guide=True,
    )
    for case in CASES:
        environment = Environment(case)
        disposition = execute_plan(environment, plan)
        assert environment.success(disposition)


def test_missing_or_repeated_checks_are_not_fabricated():
    """模拟器不根据语义近似制造成功观测。"""
    environment = Environment(CASES[0])
    with pytest.raises(InvalidCheck):
        environment.answer("observe", "任意替代文字")
    environment.answer("observe", environment.case.family.observe)
    with pytest.raises(InvalidCheck):
        environment.answer("observe", environment.case.family.observe)


def test_completed_action_does_not_invent_retest_outcome():
    """只确认执行不代表已经复测；原故障变化必须由verify显式反馈。"""
    environment = Environment(CASES[0])
    environment.answer("observe", environment.case.family.observe)
    action = environment.answer("recover", environment.case.family.action)
    assert action["executionStatus"] == "PERFORMED" and action["outcome"] == "UNKNOWN"
    assert not environment.restored_reported
    tested = environment.answer("verify", environment.case.family.instruction("verify"))
    assert tested["outcome"] == "RESTORED_REPORTED"


def test_handover_and_repair_cannot_skip_observation():
    """全交接、无前置操作和无反馈宣称恢复都不能得分。"""
    for case in CASES:
        environment = Environment(case)
        assert not environment.success("HANDOVER")
        assert not environment.success("RESOLVED")
        with pytest.raises(UnsafeAction):
            environment.answer("recover", case.family.action)
        environment.answer("observe", case.family.observe)
        if case.branch:
            with pytest.raises(UnsafeAction):
                environment.answer("recover", case.family.action)
