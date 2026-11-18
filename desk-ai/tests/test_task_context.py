"""省略上下文的按需补读、来源与并发版本边界。作者：yangaobo。"""

import json
from contextlib import asynccontextmanager
from datetime import datetime
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from pydantic import ValidationError

from servicedesk_ai.diagnosis.execution import (
    check_message,
    fact_message,
    history_message,
    represented_feedback,
)
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import tasks
from servicedesk_ai.schemas.diagnosis import ReadTaskContext
from servicedesk_ai.services.evidence import Evidence
from servicedesk_ai.services.run_tools import RunTools


class Engine:
    """隔离数据库传输；真实游标查询另在现有MySQL验收。"""

    @asynccontextmanager
    async def connect(self):
        yield object()

    begin = connect


def fixture(monkeypatch, *, source="USER_REPORT", changed=False):
    """构造被初始窗口省略的历史事实，外部来源可单独模拟撤权。"""
    run = SimpleNamespace(
        task_id="a" * 32,
        input_state_version=40,
        diagnosis_context_version=2,
        context_manifest=SimpleNamespace(facts=[], checks=[]),
    )
    task = SimpleNamespace(task_state_version=40)
    monkeypatch.setattr(
        tasks,
        "task",
        AsyncMock(side_effect=[task, SimpleNamespace(task_state_version=41 if changed else 40)]),
    )
    row = {
        "id": "b" * 32,
        "state_version": 1,
        "statement": "最初资产标签",
        "fact_kind": "OBSERVATION",
        "created_at": datetime(2026, 11, 17, 8, 0),
        "source_type": source,
        "source_run_id": "c" * 32,
        "environment_snapshot": json.dumps({"diagnosis_context_version": 1}),
    }
    query = AsyncMock(return_value=[row, row | {"id": "d" * 32}])
    monkeypatch.setattr(tasks, "context_facts", query)
    evidence = SimpleNamespace(
        inherit=AsyncMock(return_value=[]),
        verify=AsyncMock(),
        add=AsyncMock(
            return_value=Evidence(
                id="e" * 32,
                number=4,
                source_type="FACT",
                title="人工反馈",
                excerpt=row["statement"],
                locator={},
                source_version={},
                fact_id=row["id"],
            )
        ),
    )
    return RunTools(Engine(), None, None, evidence, None), run, query


@pytest.mark.asyncio
async def test_older_fact_page_uses_fixed_task_and_preserves_environment(monkeypatch):
    tool, run, query = fixture(monkeypatch)
    items, page = await tool.task_context(
        run, ReadTaskContext(section="FACTS", query="资产标签", limit=1)
    )
    assert query.call_args.args[1:] == (run.task_id, 40, None, "资产标签", 2)
    assert page[0]["hasMore"] and page[0]["nextBeforeId"] == "b" * 32
    assert page[0]["records"][0]["historicalEnvironment"] is True
    assert page[0]["records"][0]["factSourceType"] == "USER_REPORT"
    assert page[0]["records"][0]["sourceNumber"] == items[0].number
    tool.evidence.verify.assert_awaited_once_with(run)


@pytest.mark.asyncio
async def test_revoked_fact_roots_stop_before_body_is_registered(monkeypatch):
    tool, run, _ = fixture(monkeypatch, source="TOOL_OBSERVATION")
    tool.evidence.inherit.side_effect = BusinessError(CommonErrorCode.FORBIDDEN)
    with pytest.raises(BusinessError) as failure:
        await tool.task_context(run, ReadTaskContext(section="FACTS"))
    assert failure.value.error == CommonErrorCode.FORBIDDEN
    tool.evidence.add.assert_not_awaited()


@pytest.mark.asyncio
@pytest.mark.parametrize("same_version", [True, False])
async def test_initial_fact_reuse_requires_same_identity_and_version(monkeypatch, same_version):
    """仅复用本轮已筛选的同版本事实；省略项或版本不符继续完整读取。"""
    tool, run, _ = fixture(monkeypatch)
    run.context_manifest.facts = [
        SimpleNamespace(id="b" * 32, state_version=1 if same_version else 0)
    ]
    _, page = await tool.task_context(run, ReadTaskContext(section="FACTS", limit=1))
    record = page[0]["records"][0]
    if same_version:
        assert record["alreadyInInitialContext"]
        assert "statement" not in record
        tool.evidence.add.assert_not_awaited()
    else:
        assert record["statement"] == "最初资产标签"
        tool.evidence.add.assert_awaited_once()
    tool.evidence.verify.assert_awaited_once_with(run)


@pytest.mark.asyncio
async def test_initial_check_reuse_preserves_instruction_and_rechecks_sources(monkeypatch):
    """已答检查初始投影省略的原指令仍可补读，反馈去重不绕过传递来源授权。"""
    tool, run, _ = fixture(monkeypatch)
    run.context_manifest.checks = [SimpleNamespace(id="b" * 32, row_version=2, context_version=1)]
    monkeypatch.setattr(
        tasks,
        "context_checks",
        AsyncMock(
            return_value=[
                {
                    "id": "b" * 32,
                    "row_version": 2,
                    "context_version": 1,
                    "instruction": "历史完整指令",
                }
            ]
        ),
    )
    _, page = await tool.task_context(run, ReadTaskContext(section="CHECKS"))
    record = page[0]["records"][0]
    assert record["alreadyInInitialContext"] and record["instruction"] == "历史完整指令"
    tool.evidence.add.assert_not_awaited()
    tool.evidence.verify.assert_awaited_once_with(run)
    tool, run, _ = fixture(monkeypatch)
    run.context_manifest.facts = [SimpleNamespace(id="b" * 32, state_version=1)]
    tool.evidence.verify.side_effect = BusinessError(CommonErrorCode.FORBIDDEN)
    with pytest.raises(BusinessError):
        await tool.task_context(run, ReadTaskContext(section="FACTS", limit=1))


@pytest.mark.asyncio
async def test_task_changed_during_read_does_not_return_mixed_snapshot(monkeypatch):
    tool, run, _ = fixture(monkeypatch, changed=True)
    with pytest.raises(BusinessError) as failure:
        await tool.task_context(run, ReadTaskContext(section="FACTS"))
    assert failure.value.error == CommonErrorCode.CONFLICT


@pytest.mark.asyncio
async def test_context_query_escapes_wildcards_and_cannot_choose_scope():
    db = SimpleNamespace(execute=AsyncMock(return_value=SimpleNamespace(mappings=lambda: [])))
    await tasks.context_facts(db, "a" * 32, 40, None, "50%_!", 3)
    assert db.execute.call_args.args[1]["pattern"] == "%50!%!_!!%"
    for extra in ({"taskId": "b" * 32}, {"limit": 100}, {"query": " "}):
        with pytest.raises(ValidationError):
            ReadTaskContext.model_validate({"section": "FACTS"} | extra)


def test_fact_projection_retains_full_statement_and_source_without_duplicate_environment():
    """事实原文与旧环境标记不可丢失；环境字段已由固定任务范围提供。"""
    row = {
        "id": "a" * 32,
        "statement": "旧环境观察：没有执行操作。" * 30,
        "source_type": "USER_REPORT",
        "fact_kind": "SYMPTOM",
        "state_version": 1,
        "created_at": datetime(2026, 11, 17, 8, 0),
        "environment_snapshot": json.dumps(
            {"diagnosis_context_version": 1, "support_context": {"os_name": "Windows"}}
        ),
    }
    value = json.loads(fact_message(row, 2).content)["fact"]
    assert value["statement"] == row["statement"]
    assert value["id"] == row["id"] and value["sourceType"] == "USER_REPORT"
    assert value["historicalEnvironment"] and value["contextVersion"] == 1
    assert value["factKind"] == "SYMPTOM" and value["stateVersion"] == 1
    assert value["recordedAt"] == "2026-11-17T08:00:00"
    assert "environment_snapshot" not in value


def test_historical_assistant_wrapper_is_data_not_an_output_demonstration():
    """保留历史角色和原消息身份，但不让模型模仿宿主包装JSON作为最终输出。"""
    row = {"id": "a" * 32, "role": "ASSISTANT", "content": "前轮提出检查，尚未执行。"}
    message = history_message(row, "b" * 32)
    assert message.role == "user"
    payload = json.loads(message.content)
    assert payload["historicalAssistantMessageId"] == row["id"]
    assert payload["excerpt"] == row["content"]
    current = history_message(row | {"role": "USER"}, row["id"])
    assert current.content == row["content"]


def test_feedback_is_merged_only_when_selected_check_preserves_the_entire_answer():
    """关联ID和完整规范答案双重匹配；未知消息、当前提问及用户附加文本不丢失。"""
    from servicedesk_ai.schemas.tasks import CheckAnswer, NormalizedAnswer

    answer = CheckAnswer.model_validate(
        {"executionStatus": "PERFORMED", "outcome": "UNKNOWN", "value": "已操作"}
    )
    normalized = NormalizedAnswer(answer=answer, source_type="USER_REPORT")
    row = {"id": "a" * 32, "role": "USER", "content": answer.model_dump_json(exclude_none=True)}
    check = {"answer_message_id": row["id"], "normalized_answer": normalized.model_dump_json()}
    assert represented_feedback([row], [check], "b" * 32) == {row["id"]}
    assert represented_feedback([row], [], "b" * 32) == set()
    assert represented_feedback([row], [check], row["id"]) == set()
    assert (
        represented_feedback([row | {"content": row["content"] + "补充说明"}], [check], "b" * 32)
        == set()
    )
    assert represented_feedback([row | {"content": "{}"}], [check], "b" * 32) == set()


@pytest.mark.parametrize("status", ["OPEN", "NEEDS_RECHECK", "ANSWERED"])
def test_check_projection_keeps_feedback_and_procedure_identity(status):
    """待答指令不可裁剪；历史已答检查保留完整执行反馈和当前需重读的规程定位。"""
    answer = {"value": False, "execution_status": "NOT_PERFORMED", "outcome": "UNKNOWN"}
    row = {
        "id": "b" * 32,
        "check_key": "observe",
        "check_kind": "CHECK",
        "status": status,
        "normalized_answer": json.dumps(answer),
        "answer_message_id": "d" * 32,
        "answered_at": datetime(2026, 11, 17, 8, 5) if status == "ANSWERED" else None,
        "context_version": 3,
        "procedure_document_id": "c" * 32,
        "procedure_check_key": "observe",
        "instruction": "完整检查指令" * 100,
    }
    value = json.loads(check_message(row, 3).content)["check"]
    assert value["answer"] == answer and not value["historicalEnvironment"]
    assert value["procedureDocumentId"] == row["procedure_document_id"]
    assert value["procedureCheckKey"] == "observe"
    assert value["answeredAt"] == ("2026-11-17T08:05:00" if status == "ANSWERED" else None)
    assert value["instruction"] == row["instruction"]
    assert "不授予本轮重新执行权限" in value["instructionScope"]
