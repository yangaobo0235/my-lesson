"""宿主检查规程身份和真实完成前置，不能以模型判断替代。作者：yangaobo。"""

import json
from contextlib import asynccontextmanager
from copy import deepcopy
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from servicedesk_ai.diagnosis.model_request import result_schema
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import tasks
from servicedesk_ai.schemas.procedures import ProcedureCheck, ProcedureDefinition
from servicedesk_ai.schemas.runs import HypothesisSuggestion, ProcedureResult, RunResult
from servicedesk_ai.schemas.tasks import CheckProposal, HypothesisState
from servicedesk_ai.services.evidence import Evidence
from servicedesk_ai.services.run_completion import (
    ModelOutputRejected,
    RunCompletion,
    bind_procedure_dependencies,
    expand_procedure_result,
)
from servicedesk_ai.services.run_reads import citation_excerpt


def setup():
    """固定人工规程与环境，不连接外部模型或领域服务。"""
    check = ProcedureCheck.model_validate(
        {
            "check_key": "observe",
            "roles": ["CUSTOMER"],
            "instruction": "查看连接状态",
            "answer_schema": {"kind": "BOOLEAN"},
            "required_fields": ["os_name"],
            "prerequisite_check_keys": [],
            "risk_level": "LOW",
            "stop_conditions": [],
            "source_locator": {"kind": "PDF", "page_number": 1},
        }
    )
    proposal = CheckProposal(
        check_key="observe",
        check_kind="CHECK",
        instruction=check.instruction,
        answer_schema=check.answer_schema,
        dependency_fields=["os_name"],
        procedure_document_id="a" * 32,
        procedure_check_key="observe",
        source_numbers=[1],
    )
    snapshot = {"check": check.model_dump(), "required_fields": [], "definition_sha256": "b" * 64}
    task = SimpleNamespace(
        id="c" * 32,
        procedure_document_id=None,
        procedure_sha256=None,
        support_context=SimpleNamespace(os_name="Windows"),
        diagnosis_context_version=2,
    )
    run = SimpleNamespace(context_manifest=SimpleNamespace(mode="CUSTOMER_SELF_SERVICE"))
    return check, proposal, snapshot, task, run


def test_procedure_selection_uses_canonical_instruction_schema_and_identity():
    """模型只选来源号，输出完整指令/选项和环境依赖来自审核快照。"""
    check, proposal, snapshot, _, _ = setup()
    snapshot["required_fields"] = ["os_version"]
    source = Evidence(
        id="d" * 32,
        number=7,
        source_type="PROCEDURE_CHECK",
        title="审核检查",
        excerpt=json.dumps(snapshot),
        locator={},
        source_version={"procedure": snapshot},
        procedure_document_id=proposal.procedure_document_id,
        procedure_check_key=check.check_key,
    )
    selected = ProcedureResult.model_validate(
        {
            "nextAction": {"kind": "CHECKS", "selections": [{"sourceNumber": 7}]},
        }
    )
    result = expand_procedure_result(selected, [source])
    assert result.checks[0].instruction == check.instruction
    assert result.checks[0].answer_schema == check.answer_schema
    assert result.checks[0].procedure_document_id == proposal.procedure_document_id
    assert result.checks[0].dependency_fields == ["os_name", "os_version"]
    assert result.checks[0].source_numbers == result.citation_numbers == [7]
    assert result.checks[0].previous_check_id is None


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "fields,key,unknown",
    [
        ([], "ask", True),
        (["os_name"], "observe", True),
        (["os_name"], "ask", False),
        (["connection_type"], "ask", True),
        (["os_name"], "ask", True),
    ],
)
async def test_procedure_clarification_only_requests_unknown_required_environment(
    fields, key, unknown
):
    """澄清不能代替审核观察、追问已知环境或引入规程无关条件；真实缺失项允许补问。"""
    _, _, snapshot, _, _ = setup()
    source = Evidence(
        id="d" * 32,
        number=1,
        source_type="PROCEDURE_CHECK",
        title="规程",
        excerpt=json.dumps(snapshot),
        locator={},
        source_version={"procedure": snapshot},
    )
    run = SimpleNamespace(
        task_id="e" * 32,
        context_manifest=SimpleNamespace(
            support_context=SimpleNamespace(os_name=None if unknown else "Windows")
        ),
    )
    result = ProcedureResult.model_validate(
        {
            "nextAction": {
                "kind": "CLARIFY",
                "checkKey": key,
                "question": "当前操作系统是什么？",
                "dependencyFields": fields,
            },
        }
    )

    @asynccontextmanager
    async def connect():
        yield object()

    completion = RunCompletion(SimpleNamespace(connect=connect), None)
    if fields == ["os_name"] and key == "ask" and unknown:
        proposal = await completion.prepare_procedure_result(run, result, [source])
        assert proposal.checks[0].check_kind == "CLARIFICATION"
        assert proposal.checks[0].dependency_fields == ["os_name"]
    else:
        with pytest.raises(ModelOutputRejected) as error:
            await completion.prepare_procedure_result(run, result, [source])
        assert "CLARIFY只补问" in error.value.reason


@pytest.mark.parametrize("usage,kind", [("METADATA", "PROCEDURE_CHECK"), ("CONTENT", "MESSAGE")])
def test_procedure_selection_cannot_promote_history_or_user_claims(usage, kind):
    """编号存在也不代表具有本轮完整审核操作依据。"""
    source = Evidence(
        id="e" * 32,
        number=4,
        source_type=kind,
        usage_kind=usage,
        title="非操作依据",
        excerpt="",
        locator={},
        source_version={},
    )
    result = ProcedureResult.model_validate(
        {"nextAction": {"kind": "CHECKS", "selections": [{"sourceNumber": 4}]}}
    )
    with pytest.raises(ModelOutputRejected):
        expand_procedure_result(result, [source])


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "status,accepted",
    [
        (None, True),
        ("NEEDS_RECHECK", True),
        ("EXPIRED", True),
        ("FAILED", True),
        ("SKIPPED", True),
        ("WITHDRAWN", True),
        ("ANSWERED", False),
        ("OPEN", False),
    ],
)
async def test_host_binds_only_latest_recheckable_identity(monkeypatch, status, accepted):
    """新步骤不受模型误填前驱干扰；已答/待答不得借自动绑定绕过重复检查。"""
    check, proposal, snapshot, _, _ = setup()
    source = Evidence(
        id="d" * 32,
        number=7,
        source_type="PROCEDURE_CHECK",
        title="审核检查",
        excerpt=json.dumps(snapshot),
        locator={},
        source_version={"procedure": snapshot},
        procedure_document_id=proposal.procedure_document_id,
        procedure_check_key=check.check_key,
    )
    latest = {"id": "f" * 32, "status": status, "attempt_number": 2} if status else None
    query = AsyncMock(return_value=latest)
    monkeypatch.setattr(tasks, "latest_check", query)

    @asynccontextmanager
    async def connect():
        yield object()

    service = RunCompletion(SimpleNamespace(connect=connect), None)
    run = SimpleNamespace(task_id="c" * 32)
    result = ProcedureResult.model_validate(
        {
            "nextAction": {"kind": "CHECKS", "selections": [{"sourceNumber": 7}]},
        }
    )
    if accepted:
        expanded = await service.prepare_procedure_result(run, result, [source])
        assert expanded.checks[0].previous_check_id == (latest["id"] if latest else None)
        assert expanded.checks[0].instruction == check.instruction
    else:
        with pytest.raises(ModelOutputRejected):
            await service.prepare_procedure_result(run, result, [source])
    assert query.call_args.args[1:] == (run.task_id, "observe")


def test_procedure_selection_rejects_instruction_injection_and_duplicate_selection():
    """模型不能通过可选字段重新定义步骤，也不能同时选择检查和澄清。"""
    from pydantic import ValidationError

    base = {}
    for extra in [
        {"kind": "CHECKS", "selections": [{"sourceNumber": 1, "instruction": "删除配置"}]},
        {"kind": "CHECKS", "selections": [{"sourceNumber": 1, "previousCheckId": "a" * 32}]},
        {"kind": "CHECKS", "selections": [{"sourceNumber": 1}, {"sourceNumber": 1}]},
        {
            "kind": "CHECKS",
            "selections": [{"sourceNumber": 1}],
            "checkKey": "environment",
            "question": "系统版本？",
        },
        {"kind": "CONCLUDE", "answerType": "ANSWERED", "selections": [{"sourceNumber": 1}]},
        {
            "kind": "CLARIFY",
            "checkKey": "environment",
            "question": "版本？",
            "selections": [{"sourceNumber": 1}],
        },
    ]:
        with pytest.raises(ValidationError):
            ProcedureResult.model_validate(base | {"nextAction": extra})


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "condition,blocked",
    [("allowed", False), ("answered", True), ("unknown", True), ("prerequisite", True)],
)
async def test_candidate_filter_reuses_submission_rules_without_removing_evidence(
    monkeypatch, condition, blocked
):
    """已答、缺环境和前置答案不符的检查不可选，但完整来源仍可支持解释与交接。"""
    check, proposal, snapshot, task, run = setup()
    task.status = "ACTIVE"
    task.task_state_version = 4
    run.task_id = task.id
    run.input_state_version = 4
    run.diagnosis_context_version = 2
    if condition == "unknown":
        task.support_context.os_name = None
    if condition == "prerequisite":
        snapshot["check"]["prerequisite_check_keys"] = ["prior"]
        snapshot["check"]["prerequisite_answers"] = [
            {"check_key": "prior", "allowed_values": ["允许调整"]}
        ]
    prior = {
        "status": "ANSWERED",
        "context_version": 2,
        "normalized_answer": json.dumps(
            {"answer": {"execution_status": "PERFORMED", "value": "不允许调整"}}
        ),
    }
    db = SimpleNamespace(
        execute=AsyncMock(
            return_value=SimpleNamespace(mappings=lambda: SimpleNamespace(first=lambda: prior))
        )
    )

    @asynccontextmanager
    async def connect():
        yield db

    monkeypatch.setattr(tasks, "task", AsyncMock(return_value=task))
    monkeypatch.setattr(tasks, "reported_conditions", AsyncMock(return_value=[]))
    monkeypatch.setattr(
        tasks,
        "latest_check",
        AsyncMock(return_value={"status": "ANSWERED"} if condition == "answered" else None),
    )
    source = Evidence(
        id="d" * 32,
        number=7,
        source_type="PROCEDURE_CHECK",
        title="审核检查",
        excerpt=json.dumps(snapshot),
        locator={},
        source_version={"procedure": snapshot},
        procedure_document_id=proposal.procedure_document_id,
        procedure_check_key=check.check_key,
    )
    original = deepcopy(source)
    service = RunCompletion(SimpleNamespace(connect=connect), None)
    unavailable = await service.unavailable_procedure_checks(run, [source])
    assert (7 in unavailable) == blocked
    assert source == original
    schema = result_schema(True, [source], set(unavailable))
    assert schema["properties"]["citationNumbers"]["items"]["enum"] == [7]
    branches = schema["properties"]["nextAction"]["anyOf"]
    assert ({"$ref": "#/$defs/ProcedureChecks"} in branches) != blocked
    if condition == "allowed":
        task.task_state_version += 1
        with pytest.raises(BusinessError) as changed:
            await service.unavailable_procedure_checks(run, [source])
        assert changed.value.error == CommonErrorCode.CONFLICT


def test_model_schema_limits_identifiers_to_current_content_sources():
    """历史元信息和用户声称的ID不能出现在模型可选身份中；没有事实时不得编造支持ID。"""

    def source(number, kind, usage="CONTENT", fact=None):
        return Evidence(
            id=f"{number:032x}",
            number=number,
            source_type=kind,
            usage_kind=usage,
            title="来源",
            excerpt="",
            locator={},
            source_version={"procedure": setup()[2]} if kind == "PROCEDURE_CHECK" else {},
            fact_id=fact,
        )

    evidence = [
        source(1, "MESSAGE"),
        source(2, "PROCEDURE_CHECK"),
        source(3, "FACT", fact="f" * 32),
        source(4, "PROCEDURE_CHECK", "METADATA"),
    ]
    schema = result_schema(True, evidence)
    assert schema["properties"]["citationNumbers"]["items"]["enum"] == [1, 2, 3]
    assert schema["$defs"]["ProcedureSelection"]["properties"]["sourceNumber"]["enum"] == [2]
    assert schema["$defs"]["HypothesisSuggestion"]["properties"]["supportingFactIds"]["items"][
        "enum"
    ] == ["f" * 32]
    empty = result_schema(False, [])
    assert empty["properties"]["citationNumbers"]["maxItems"] == 0
    assert (
        empty["$defs"]["HypothesisSuggestion"]["properties"]["supportingFactIds"]["maxItems"] == 0
    )
    assert empty["$defs"]["CheckProposal"]["properties"]["sourceNumbers"]["maxItems"] == 0


def test_procedure_clarification_expands_only_to_nonoperative_text_question():
    """缺少必要条件先产生澄清，不使用规程来源号伪装成已执行检查。"""
    result = ProcedureResult.model_validate(
        {
            "nextAction": {
                "kind": "CLARIFY",
                "checkKey": "environment",
                "question": "系统版本是什么？",
                "dependencyFields": ["os_version"],
            },
        }
    )
    expanded = expand_procedure_result(result, [])
    assert expanded.checks[0].check_kind == "CLARIFICATION"
    assert expanded.checks[0].answer_schema.kind == "TEXT"
    assert expanded.checks[0].procedure_document_id is None
    assert expanded.answer_type == "CLARIFICATION"


@pytest.mark.parametrize("answer_type", ["ANSWERED", "HANDOVER", "INSUFFICIENT_EVIDENCE"])
def test_procedure_conclusion_preserves_cited_report_without_creating_checks(answer_type):
    """结束本轮保留显式结论及引用，不强行把回答改成交接或创建新检查。"""
    selected = ProcedureResult.model_validate(
        {
            "citationNumbers": [3],
            "nextAction": {
                "kind": "CONCLUDE",
                "answerType": answer_type,
                "feedbackSourceNumber": 3 if answer_type == "ANSWERED" else None,
            },
        }
    )
    result = expand_procedure_result(selected, [])
    assert result.answer_type == answer_type
    assert result.citation_numbers == [3] and result.checks == []


@pytest.mark.asyncio
async def test_host_rejects_modified_instruction_or_unknown_environment():
    """审核指引被模型改写或关键环境未知，不能创建可操作检查。"""
    _, proposal, snapshot, task, run = setup()
    service = RunCompletion(None, None)
    await service.validate_procedure_check(None, run, task, proposal, snapshot)
    with pytest.raises(BusinessError):
        await service.validate_procedure_check(
            None, run, task, proposal.model_copy(update={"instruction": "重置整个网络"}), snapshot
        )
    task.support_context.os_name = None
    with pytest.raises(BusinessError):
        await service.validate_procedure_check(None, run, task, proposal, snapshot)


@pytest.mark.asyncio
async def test_correction_identifies_check_kind_without_masking_revision_conflict():
    """模型字段错误反馈具体纠正对象；真实修订不一致仍终止，不作为输出纠正。"""
    _, proposal, snapshot, task, run = setup()
    service = RunCompletion(None, None)
    with pytest.raises(ModelOutputRejected) as rejected:
        await service.validate_procedure_check(
            None, run, task, proposal.model_copy(update={"check_kind": "CLARIFICATION"}), snapshot
        )
    assert "checkKind必须为CHECK" in rejected.value.reason
    assert "保留该检查的正确来源号" in rejected.value.reason
    task.procedure_document_id = proposal.procedure_document_id
    task.procedure_sha256 = "f" * 64
    with pytest.raises(BusinessError) as changed:
        await service.validate_procedure_check(None, run, task, proposal, snapshot)
    assert changed.value.error == CommonErrorCode.CONFLICT
    assert not isinstance(changed.value, ModelOutputRejected)


@pytest.mark.asyncio
async def test_hypothesis_cannot_promote_unread_fact_id_to_evidence(monkeypatch):
    """用户知道历史factId不代表本轮读取授权，假设不能绕过实际来源登记。"""
    active = AsyncMock()
    monkeypatch.setattr("servicedesk_ai.services.run_completion.tasks.active_fact", active)
    db = SimpleNamespace(execute=AsyncMock(return_value=SimpleNamespace(scalars=lambda: [])))
    service = RunCompletion(None, None)
    result = RunResult(
        answer_type="ANSWERED",
        text="假设",
        hypotheses=[
            HypothesisSuggestion(
                hypothesis_key="possible_cause",
                statement="用户提供了旧事实标识",
                status="SUPPORTED",
                supporting_fact_ids=["a" * 32],
            )
        ],
    )
    with pytest.raises(ModelOutputRejected) as rejected:
        await service.hypotheses(
            db,
            SimpleNamespace(id="b" * 32),
            SimpleNamespace(hypothesis_state=HypothesisState(), diagnosis_context_version=1),
            result,
        )
    assert "本轮已读取的factId" in rejected.value.reason
    active.assert_not_awaited()


def test_host_binds_required_dependencies_even_when_model_omits_them():
    """漏写元数据不能导致合法检查失败或失去环境变化失效能力；指令仍保持原样校验。"""
    _, proposal, snapshot, _, _ = setup()
    snapshot["required_fields"] = ["os_version"]
    omitted = proposal.model_copy(update={"dependency_fields": ["error_code"]})
    bound = bind_procedure_dependencies(omitted, snapshot)
    assert set(bound.dependency_fields) == {"os_name", "os_version", "error_code"}
    assert omitted.dependency_fields == ["error_code"]
    assert bound.instruction == proposal.instruction


def test_procedure_citation_preserves_global_and_local_safety_constraints():
    """人工引用不展示内部JSON，但不得遗漏审核全局停止条件或实际检查指令。"""
    check, _, snapshot, _, _ = setup()
    snapshot.update(
        schema_version=1,
        applicability="办公网络打印",
        required_fields=["os_version"],
        stop_conditions=["SAFETY_HAZARD_REPORTED"],
        handover_conditions=["ADMIN_PRIVILEGE_REQUIRED"],
    )
    snapshot["check"]["stop_conditions"] = ["EVIDENCE_CONFLICT"]
    item = Evidence(
        id="d" * 32,
        number=1,
        source_type="PROCEDURE_CHECK",
        title="审核规程",
        excerpt="原始快照",
        locator={},
        source_version={"procedure": snapshot},
    )
    display = citation_excerpt(item)
    assert check.instruction in display
    assert "报告安全危险" in display and "证据相互冲突" in display
    assert "需要管理员权限" in display
    assert "操作系统" in display and "系统版本" in display
    assert "definition_sha256" not in display
    assert item.excerpt == "原始快照"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "performed,version,accepted",
    [
        ("NOT_PERFORMED", 2, False),
        ("PERFORMED", 1, False),
        ("PERFORMED", 2, True),
    ],
)
async def test_answered_does_not_mean_prerequisite_performed(performed, version, accepted):
    """答复过、实际执行过和当前环境仍适用是三个独立条件。"""
    _, proposal, snapshot, task, run = setup()
    snapshot["check"]["prerequisite_check_keys"] = ["prior"]
    row = {
        "status": "ANSWERED",
        "context_version": version,
        "normalized_answer": '{"answer":{"execution_status":"' + performed + '"}}',
    }
    db = SimpleNamespace(
        execute=AsyncMock(
            return_value=SimpleNamespace(mappings=lambda: SimpleNamespace(first=lambda: row))
        )
    )
    service = RunCompletion(None, None)
    if accepted:
        await service.validate_procedure_check(db, run, task, proposal, snapshot)
    else:
        with pytest.raises(BusinessError):
            await service.validate_procedure_check(db, run, task, proposal, snapshot)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "value,status,accepted",
    [
        ("允许自行调整", "ANSWERED", True),
        ("提示无权访问", "ANSWERED", False),
        (None, "OBSERVED_DONE", False),
        ("提示无权访问", "OBSERVED_DONE", False),
    ],
)
async def test_prerequisite_answer_is_enforced_after_actual_execution(value, status, accepted):
    """已执行和工具已观测都不能绕过规程答案限制；失败提议不得创建可操作检查。"""
    _, proposal, snapshot, task, run = setup()
    snapshot["check"].update(
        prerequisite_check_keys=["prior"],
        prerequisite_answers=[
            {"check_key": "prior", "allowed_values": ["允许自行调整"]},
        ],
    )
    row = {
        "status": status,
        "context_version": 2,
        "normalized_answer": json.dumps(
            {
                "answer": {"execution_status": "PERFORMED", "value": value},
            }
        ),
    }
    db = SimpleNamespace(
        execute=AsyncMock(
            return_value=SimpleNamespace(mappings=lambda: SimpleNamespace(first=lambda: row))
        )
    )
    service = RunCompletion(None, None)
    if accepted:
        await service.validate_procedure_check(db, run, task, proposal, snapshot)
    else:
        with pytest.raises(ModelOutputRejected):
            await service.validate_procedure_check(db, run, task, proposal, snapshot)


def test_definition_rejects_nonexistent_options_and_forward_answer_references():
    """有限条件只可引用较早选择题的原选项，不允许暗含额外依赖或自造谓词。"""
    from pydantic import ValidationError

    check, _, _, _, _ = setup()
    prior = check.model_dump() | {
        "check_key": "prior",
        "answer_schema": {
            "kind": "CHOICE",
            "options": ["允许自行调整", "提示无权访问"],
        },
    }
    current = check.model_dump() | {
        "prerequisite_check_keys": ["prior"],
        "prerequisite_answers": [
            {"check_key": "prior", "allowed_values": ["允许自行调整"]},
        ],
    }
    definition = {
        "schema_version": 1,
        "applicability": "测试",
        "required_fields": [],
        "stop_conditions": [],
        "handover_conditions": [],
        "checks": [prior, current],
    }
    assert ProcedureDefinition.model_validate(definition).checks[1].prerequisite_answers
    current["prerequisite_answers"][0]["allowed_values"] = ["虚构答案"]
    with pytest.raises(ValidationError):
        ProcedureDefinition.model_validate(definition)
    current["prerequisite_answers"][0]["allowed_values"] = ["允许自行调整"]
    current["prerequisite_check_keys"] = []
    with pytest.raises(ValidationError):
        ProcedureDefinition.model_validate(definition)


def recovery_evidence(outcome="RESTORED_REPORTED", context=2, execution="PERFORMED"):
    """人工反馈来源保留检查身份及环境，不把用户任意消息视作结构化复测。"""
    return Evidence(
        id="e" * 32,
        number=3,
        source_type="MESSAGE",
        title="检查反馈",
        excerpt=json.dumps(
            {
                "context_version": context,
                "answer": {
                    "source_type": "USER_REPORT",
                    "answer": {
                        "value": "实际观察",
                        "execution_status": execution,
                        "outcome": outcome,
                    },
                },
            }
        ),
        locator={},
        source_version={"check_id": "d" * 32, "row_version": 2},
    )


@pytest.mark.parametrize(
    "outcome,context,execution,allowed",
    [
        ("RESTORED_REPORTED", 2, "PERFORMED", True),
        ("RESTORED_REPORTED", 1, "PERFORMED", False),
        ("UNKNOWN", 2, "PERFORMED", False),
        ("UNCHANGED", 2, "PERFORMED", False),
        ("UNKNOWN", 2, "NOT_PERFORMED", False),
    ],
)
def test_recovery_selection_requires_current_explicit_retest(outcome, context, execution, allowed):
    """完成操作、原环境恢复和无变化不能进入当前恢复的可选来源号。"""
    schema = result_schema(
        True, [recovery_evidence(outcome, context, execution)], context_version=2
    )
    fields = schema["$defs"]["ProcedureConclusion"]["properties"]
    assert (3 in fields["feedbackSourceNumber"]["enum"]) == allowed
    assert ("ANSWERED" in fields["answerType"]["enum"]) == allowed
    assert "text" not in schema["properties"]


def test_procedure_status_rejects_model_prose_and_requires_recovery_reference():
    """状态说明由宿主产生，模型不能加入无依据的恢复原因或省略恢复来源。"""
    from pydantic import ValidationError

    action = {"kind": "CONCLUDE", "answerType": "ANSWERED", "feedbackSourceNumber": 3}
    with pytest.raises(ValidationError):
        ProcedureResult.model_validate({"text": "已经现场修好", "nextAction": action})
    with pytest.raises(ValidationError):
        ProcedureResult.model_validate({"nextAction": action | {"feedbackSourceNumber": None}})
    result = expand_procedure_result(ProcedureResult.model_validate({"nextAction": action}), [])
    assert result.citation_numbers == [3]
    assert "您的反馈记录" in result.text and "尚未经现场实测核验" in result.text


@pytest.mark.parametrize(
    "extra",
    [
        {},
        {"proposed_record": "待核对记录"},
        {
            "proposed_resolution": "建议人工接手",
            "resolution_type": "REFERRED",
            "referral_target": "支持组",
        },
        {
            "hypotheses": [
                {"hypothesis_key": "cause", "statement": "可能的原因", "status": "PROPOSED"}
            ]
        },
        {
            "checks": [
                {
                    "check_key": "ask",
                    "check_kind": "CLARIFICATION",
                    "instruction": "版本是什么？",
                    "dependency_fields": ["os_version"],
                    "answer_schema": {"kind": "TEXT"},
                }
            ]
        },
    ],
)
def test_free_analysis_and_clarification_still_require_review(extra):
    """跳过的是宿主确定的文字；自由假设、草稿、澄清及普通RAG继续审核。"""
    from servicedesk_ai.diagnosis.model_request import needs_model_review

    result = RunResult.model_validate({"text": "宿主状态", "answer_type": "HANDOVER"} | extra)
    assert needs_model_review(result, True) == bool(extra)
    assert needs_model_review(result, False)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "condition", ["valid", "missing", "stale", "other_check", "unknown", "risk"]
)
async def test_recovery_guard_revalidates_persisted_feedback(monkeypatch, condition):
    """来源版本、反馈与当前风险在持久化边界重新判定，不能只信模型选择。"""
    source = recovery_evidence("UNKNOWN" if condition == "unknown" else "RESTORED_REPORTED")
    row = {
        "id": "d" * 32,
        "row_version": 3 if condition == "stale" else 2,
        "source_version": json.dumps(
            source.source_version | ({"check_id": "f" * 32} if condition == "other_check" else {})
        ),
        "normalized_answer": json.dumps(json.loads(source.excerpt)["answer"]),
    }
    db = SimpleNamespace(
        execute=AsyncMock(
            return_value=SimpleNamespace(
                mappings=lambda: SimpleNamespace(
                    first=lambda: None if condition == "missing" else row
                )
            )
        )
    )
    monkeypatch.setattr(
        tasks,
        "reported_conditions",
        AsyncMock(return_value={"RISK"} if condition == "risk" else set()),
    )
    run = SimpleNamespace(id="a" * 32, task_id="b" * 32, diagnosis_context_version=2)
    service = RunCompletion(None, None)
    if condition == "valid":
        await service.validate_recovery_feedback(db, run, 3)
    else:
        with pytest.raises(ModelOutputRejected):
            await service.validate_recovery_feedback(db, run, 3)


@pytest.mark.asyncio
async def test_procedure_decision_uses_host_output_without_semantic_judge(monkeypatch):
    """实际执行循环选择审核检查后仍经过完成边界，不再审核宿主复述的状态文字。"""
    from servicedesk_ai.diagnosis.execution import RunWorker
    from servicedesk_ai.infrastructure.models import ModelMessage

    @asynccontextmanager
    async def connection():
        yield object()

    check, proposal, snapshot, _, _ = setup()
    source = Evidence(
        id="d" * 32,
        number=7,
        source_type="PROCEDURE_CHECK",
        title="审核检查",
        excerpt=json.dumps(snapshot),
        locator={},
        source_version={"procedure": snapshot},
        procedure_document_id=proposal.procedure_document_id,
        procedure_check_key=check.check_key,
    )
    run = SimpleNamespace(
        id="a" * 32,
        task_id="b" * 32,
        diagnosis_context_version=2,
        context_manifest=SimpleNamespace(
            checks=[],
            service_item_id="c" * 32,
            support_context=SimpleNamespace(os_name="Windows", os_version="11"),
        ),
        pipeline_config=SimpleNamespace(max_model_calls=5, max_input_tokens=12000),
    )
    worker = RunWorker.__new__(RunWorker)
    worker.engine = SimpleNamespace(connect=connection)
    worker.verify = AsyncMock()
    worker.hazard_report = AsyncMock(return_value=False)
    worker.context_builder = SimpleNamespace(
        build=AsyncMock(return_value=(run, [ModelMessage(role="user", content="请排查")]))
    )
    worker.evidence = SimpleNamespace(list_items=AsyncMock(return_value=[source]))
    worker.completion = RunCompletion(worker.engine, None)
    worker.completion.unavailable_procedure_checks = AsyncMock(return_value={})
    worker.completion.has_diagnostic_basis = AsyncMock(return_value=True)
    worker.tools = SimpleNamespace(definitions=lambda run: [])
    worker.review_answer = AsyncMock()
    worker.finish = AsyncMock()
    selected = ProcedureResult.model_validate(
        {"nextAction": {"kind": "CHECKS", "selections": [{"sourceNumber": 7}]}}
    )
    worker.models = SimpleNamespace(
        chat=AsyncMock(
            return_value=SimpleNamespace(
                call_id="f" * 32,
                response=SimpleNamespace(
                    message=ModelMessage(
                        role="assistant", content=selected.model_dump_json(by_alias=True)
                    )
                ),
            )
        )
    )
    monkeypatch.setattr(tasks, "reported_conditions", AsyncMock(return_value=[]))
    monkeypatch.setattr(tasks, "latest_check", AsyncMock(return_value=None))
    monkeypatch.setattr(
        tasks,
        "task",
        AsyncMock(return_value=SimpleNamespace(budget_policy=SimpleNamespace(max_model_calls=24))),
    )
    monkeypatch.setattr(
        "servicedesk_ai.diagnosis.execution.usage", AsyncMock(return_value=SimpleNamespace(calls=0))
    )
    await worker.execute_bounded(run)
    worker.review_answer.assert_not_awaited()
    worker.finish.assert_awaited_once()
    result = worker.finish.call_args.args[1]
    assert result.checks[0].instruction == check.instruction
    assert "完成操作不代表原问题已经恢复" in result.text


@pytest.mark.asyncio
async def test_recovery_is_revalidated_inside_final_transaction(monkeypatch):
    """准备阶段通过也不能跳过提交时锁任务后的恢复复核，拒绝后不得保存终态。"""
    import servicedesk_ai.services.run_completion as completion_module

    operations = []
    db = SimpleNamespace(execute=AsyncMock())

    @asynccontextmanager
    async def connection():
        yield db

    @asynccontextmanager
    async def transaction():
        operations.append("begin")
        yield db

    task = SimpleNamespace(status="ACTIVE", task_state_version=4, diagnosis_context_version=2)

    async def load_task(db, task_id, lock=False):
        if lock:
            operations.append("task_locked")
        return task

    async def revalidate(db, run, source_number):
        operations.append("recovery_checked")
        raise ModelOutputRejected("反馈已失效")

    monkeypatch.setattr(tasks, "task", load_task)
    monkeypatch.setattr(
        completion_module, "scope", AsyncMock(return_value=SimpleNamespace(ticket_id=None))
    )
    monkeypatch.setattr(completion_module.runs, "owned", AsyncMock())
    service = RunCompletion(
        SimpleNamespace(connect=connection, begin=transaction),
        SimpleNamespace(authorize=AsyncMock(return_value=(None, None)), owner=lambda *args: None),
    )
    service.validate_recovery_feedback = revalidate
    run = SimpleNamespace(
        task_id="a" * 32,
        conversation_id="b" * 32,
        input_state_version=4,
        diagnosis_context_version=2,
    )
    with pytest.raises(ModelOutputRejected):
        await service.commit(
            None,
            run,
            RunResult(text="用户报告恢复", answer_type="ANSWERED"),
            recovery_source_number=3,
        )
    assert operations == ["begin", "task_locked", "recovery_checked"]
    db.execute.assert_not_awaited()


def test_clarification_schema_only_exposes_missing_procedure_environment():
    """环境已明确时不提供自由澄清分支；缺项时只能选择该审核规程的具体依赖。"""
    _, _, snapshot, _, _ = setup()
    source = Evidence(
        id="a" * 32,
        number=1,
        source_type="PROCEDURE_CHECK",
        title="规程",
        excerpt=json.dumps(snapshot),
        locator={},
        source_version={"procedure": snapshot},
    )
    for known in [True, False]:
        schema = result_schema(
            True, [source], environment=SimpleNamespace(os_name="Windows" if known else None)
        )
        branches = schema["properties"]["nextAction"]["anyOf"]
        assert ({"$ref": "#/$defs/ProcedureClarification"} in branches) != known
        if not known:
            field = schema["$defs"]["ProcedureClarification"]["properties"]["dependencyFields"]
            assert field["items"]["enum"] == ["os_name"] and field["minItems"] == 1


@pytest.mark.parametrize(
    "mixed,loaded,forced", [(False, False, True), (True, False, False), (False, True, False)]
)
def test_procedure_acquisition_precedes_free_document_reading(mixed, loaded, forced):
    """只有纯审核候选且尚未加载时固定取证阶段，不改变普通资料或混合资料的选择能力。"""
    from servicedesk_ai.diagnosis.model_request import discovery_tools
    from servicedesk_ai.infrastructure.models import ModelTool

    source = Evidence(
        id="a" * 32,
        number=1,
        source_type="CHUNK",
        title="规程",
        excerpt="",
        locator={},
        source_version={"knowledge_source": {"procedure_check_keys": ["observe"]}},
    )
    sources = [source]
    if mixed:
        sources.append(
            Evidence(
                id="b" * 32,
                number=2,
                source_type="CHUNK",
                title="普通资料",
                excerpt="",
                locator={},
                source_version={"knowledge_source": {"procedure_check_keys": []}},
            )
        )
    if loaded:
        sources.append(
            Evidence(
                id="c" * 32,
                number=3,
                source_type="PROCEDURE_CHECK",
                title="已读检查",
                excerpt="",
                locator={},
                source_version={},
            )
        )
    tools = [
        ModelTool(name=name, description="取证", parameters={})
        for name in ["search_knowledge", "read_evidence", "load_procedure"]
    ]
    selected, required = discovery_tools(tools, sources, 8)
    assert (required == "load_procedure") == forced
    if forced:
        assert [tool.name for tool in selected] == ["load_procedure"]
    ablated, required = discovery_tools(tools[:2], sources, 8)
    assert required is None and len(ablated) == 2


@pytest.mark.parametrize("remaining,search_allowed", [(6, True), (5, False), (4, False)])
def test_search_availability_includes_embedding_and_finalization_budget(remaining, search_allowed):
    """下一次Chat加查询Embedding仍须留四次收束预算，不把搜索当作免费只读。"""
    from servicedesk_ai.diagnosis.model_request import discovery_tools
    from servicedesk_ai.infrastructure.models import ModelTool

    definitions = [
        ModelTool(name=name, description="取证", parameters={})
        for name in ["search_knowledge", "read_evidence"]
    ]
    selected, required = discovery_tools(definitions, [], remaining)
    assert ("search_knowledge" in {tool.name for tool in selected}) == search_allowed
    assert "read_evidence" in {tool.name for tool in selected} and required is None
