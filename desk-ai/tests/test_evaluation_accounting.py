"""评测发生读取/受理未知时不得把部分账目当作完整费用报告。作者：yangaobo。"""

import pytest

from servicedesk_ai.infrastructure.models import ModelProviderError
from tests.evaluation import __main__ as entry
from tests.evaluation.environment import Trial, summarize


@pytest.mark.parametrize("invalid", ["duplicate", "unknown", "boolean", "empty"])
def test_explicit_retry_selection_rejects_ambiguous_identity(tmp_path, invalid):
    """补测只认明确实例身份，重复或越界清单不得产生新的付费批次。"""
    import json
    from types import SimpleNamespace

    from tests.evaluation.catalog import CASES

    row = {"caseId": CASES[0].identifier, "strategy": "dynamic", "repeat": 1}
    rows = [row, row] if invalid == "duplicate" else [] if invalid == "empty" else [row]
    if invalid == "unknown":
        row["caseId"] = "missing"
    if invalid == "boolean":
        row["repeat"] = True
    path = tmp_path / "selection.json"
    path.write_text(json.dumps(rows), encoding="utf-8")
    args = SimpleNamespace(selection=path, strategies=["dynamic"], repetitions=3)
    with pytest.raises(SystemExit):
        entry.select_trials(args, CASES)


def test_explicit_retry_selection_keeps_original_repetition(tmp_path):
    """只补第2次失败时不偷偷再跑第1次成功样例。"""
    import json
    from types import SimpleNamespace

    from tests.evaluation.catalog import CASES

    path = tmp_path / "selection.json"
    path.write_text(
        json.dumps([{"caseId": CASES[0].identifier, "strategy": "dynamic", "repeat": 2}]),
        encoding="utf-8",
    )
    args = SimpleNamespace(selection=path, strategies=["dynamic"], repetitions=3)
    assert entry.select_trials(args, CASES) == {(CASES[0].identifier, "dynamic", 2)}


@pytest.mark.parametrize("failure", ["provider_rejection", "returned_unknown", "returned_running"])
def test_dependency_failure_stops_batch_after_first_attempt(tmp_path, monkeypatch, failure):
    """供应商拒绝或动态流程返回未知时保存本例并停止，不能继续付费批次。"""
    import json
    from types import SimpleNamespace

    from servicedesk_ai.bootstrap.settings import Settings
    from tests.evaluation.catalog import FAMILIES

    family = FAMILIES[0]
    bindings = tmp_path / "bindings.json"
    bindings.write_text(json.dumps({"families": {family.key: {"ready": True}}}), encoding="utf-8")
    arguments = SimpleNamespace(
        command="run",
        split="all",
        family=family.key,
        case=None,
        repetitions=3,
        strategies=["single_rag" if failure == "provider_rejection" else "dynamic"],
        accounts=tmp_path / "unused",
        bindings=bindings,
        output=tmp_path / "batch",
        selection=None,
        ablation="full",
        scoped_worker=False,
        base_url="http://unused.invalid",
    )
    monkeypatch.setattr(entry, "parser", lambda: SimpleNamespace(parse_args=lambda: arguments))
    monkeypatch.setattr(entry, "get_settings", lambda: Settings(infrastructure_enabled=False))
    monkeypatch.setattr(
        entry, "Client", lambda *args: SimpleNamespace(close=lambda: None, call=lambda path: {})
    )
    attempts = []

    async def rejected(*args):
        attempts.append(True)
        raise ModelProviderError("ACCOUNT_UNAVAILABLE", rejected=True)

    def unknown(client, case, binding, trial, save, **kwargs):
        attempts.append(True)
        trial.status = "RUNNING" if failure == "returned_running" else "UNKNOWN"

    monkeypatch.setattr(entry, "baseline", rejected)
    monkeypatch.setattr(entry, "dynamic", unknown)
    entry.main()
    report = json.loads((arguments.output / "summary.json").read_text(encoding="utf-8"))
    assert len(attempts) == 1
    assert report["recordedTrials"] == 1 and report["plannedTrials"] == 9
    assert report["haltedByDependencyOrQuota"] is True
    if failure in {"returned_unknown", "returned_running"}:
        attempts.clear()
        entry.main()
        assert not attempts, "重启驱动不能跳过未对账的UNKNOWN/RUNNING继续付费"


@pytest.mark.parametrize("failure", ["poll_failed", "admission_unknown", "baseline_in_flight"])
def test_interrupted_accounting_is_explicit_and_never_reported_as_free(failure):
    """即使记录中没有任何用量，远端可能已经执行，必须标明账目待核对。"""
    trial = Trial(
        case_id="sample-1",
        family="sample",
        split="development",
        repeat=1,
        strategy="dynamic",
        expected="RESOLVED",
        status="FAILED",
        outputs=[{"pendingRunRequest": "original-request"}],
    )
    if failure == "poll_failed":
        trial.run_ids = ["persisted-run"]
    elif failure == "baseline_in_flight":
        trial.strategy = "single_rag"
        trial.outputs = []
        trial.model_calls = [{"status": "IN_FLIGHT"}]
    group = summarize([trial])["strategies"][trial.strategy]
    assert group["accounting"]["complete"] is False
    assert group["usage"]["estimatedCost"] is None
    assert group["outcomes"]["RESOLVED"]["total"] == 1
    assert group["outcomes"]["RESOLVED"]["passed"] == 0
    if failure == "poll_failed":
        assert group["accounting"]["runsWithoutTerminalLedger"] == ["persisted-run"]
    elif failure == "admission_unknown":
        assert group["accounting"]["requestsWithoutRecordedRun"] == 1
    else:
        assert group["accounting"]["inFlightCalls"] == 1


def test_terminal_failure_keeps_complete_ledger_and_unknown_usage_separate():
    """已拿到失败Run完整账目仍可能有未知计量，两种缺口不能混为一谈。"""
    trial = Trial(
        case_id="sample-1",
        family="sample",
        split="development",
        repeat=1,
        strategy="dynamic",
        expected="RESOLVED",
        status="FAILED",
        run_ids=["persisted-run"],
        outputs=[
            {"pendingRunRequest": "original-request"},
            {"run": {"id": "persisted-run", "status": "INTERRUPTED"}, "toolCalls": []},
        ],
        model_calls=[
            {"runId": "persisted-run", "number": 1, "status": "UNKNOWN"},
        ],
    )
    group = summarize([trial])["strategies"]["dynamic"]
    assert group["accounting"]["complete"] is True
    assert group["usage"]["calls"] == 1
    assert group["usage"]["callsWithUnknownUsage"] == 1
    assert group["usage"]["estimatedCost"] is None
    assert group["outcomes"]["RESOLVED"]["passed"] == 0


def test_dynamic_dependency_failure_retains_run_and_stops_driver():
    """已受理运行的内部RPC故障向批次传播停止信号，保留原运行和账目。"""
    from tests.evaluation.catalog import CASES
    from tests.evaluation.client import ApiFailure
    from tests.evaluation.strategies import dynamic

    class Client:
        """只替换HTTP传输，驱动执行真实的请求身份和失败分类。"""

        def call(self, path, body=None):
            """按真实端点返回依赖故障终态，不调用外部资源。"""
            if path == "ai/conversations":
                return {"id": "conversation"}
            if path.endswith("/tasks") or path == "ai/tasks/task":
                return {"taskId": "task", "taskStateVersion": "1"}
            if path.endswith("/runs"):
                return {"id": "run"}
            if path == "ai/runs/run":
                return {"id": "run", "status": "FAILED", "errorCode": "DEPENDENCY_UNAVAILABLE"}
            if path.endswith("/calls"):
                return {"modelCalls": [], "toolCalls": []}
            raise AssertionError(path)

    case = CASES[0]
    trial = Trial(
        case_id=case.identifier,
        family=case.family.key,
        split=case.family.split,
        repeat=1,
        strategy="dynamic",
        expected=case.expected,
        status="RUNNING",
    )
    with pytest.raises(ApiFailure) as failure:
        dynamic(Client(), case, {"serviceItemId": "service"}, trial, lambda: None)
    assert failure.value.code == 1011
    assert trial.status == "FAILED" and trial.run_ids == ["run"]
    assert trial.outputs[-1]["run"]["errorCode"] == "DEPENDENCY_UNAVAILABLE"


@pytest.mark.parametrize(
    "answer_type,expected",
    [("ANSWERED", "RESOLVED"), ("INSUFFICIENT_EVIDENCE", "INSUFFICIENT_EVIDENCE")],
)
def test_safe_refusal_after_simulated_recovery_is_not_success(monkeypatch, answer_type, expected):
    """模拟器已恢复也必须有可交付回答；拒绝不算任务完成，旧批次不重写。"""
    from types import SimpleNamespace

    from tests.evaluation import strategies
    from tests.evaluation.catalog import CASES

    monkeypatch.setattr(
        strategies,
        "Environment",
        lambda case: SimpleNamespace(
            restored_reported=True, success=lambda disposition: disposition == "RESOLVED"
        ),
    )

    class Client:
        """仅提供本例的HTTP响应，不访问真实模型或数据库。"""

        def call(self, path, body=None):
            if path == "ai/conversations":
                return {"id": "conversation"}
            if path.endswith("/tasks") or path == "ai/tasks/task":
                return {"taskId": "task", "taskStateVersion": "1"}
            if path.endswith("/runs"):
                return {"id": "run"}
            if path == "ai/runs/run":
                return {
                    "id": "run",
                    "status": "SUCCEEDED",
                    "structuredResult": {"answerType": answer_type, "checks": []},
                }
            if path.endswith("/calls"):
                return {"modelCalls": [], "toolCalls": []}
            raise AssertionError(path)

    case = CASES[0]
    trial = Trial(
        case_id=case.identifier,
        family=case.family.key,
        split=case.family.split,
        repeat=1,
        strategy="dynamic",
        expected="RESOLVED",
        status="RUNNING",
    )
    strategies.dynamic(Client(), case, {"serviceItemId": "service"}, trial, lambda: None)
    assert trial.disposition == expected
    assert trial.success == (answer_type == "ANSWERED")
