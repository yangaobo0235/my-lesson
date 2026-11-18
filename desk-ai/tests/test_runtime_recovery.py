"""进程恢复、工具拒绝及异步日志边界回归；不破坏共享真实组件。作者：yangaobo。"""

import asyncio
import json
import logging
import runpy
from contextlib import asynccontextmanager
from pathlib import Path
from threading import Event
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock

import pytest
from opentelemetry.sdk.trace import TracerProvider
from sqlalchemy.exc import OperationalError

from servicedesk_ai import receiver
from servicedesk_ai.bootstrap.observability import BoundedLogHandler, JsonFormatter
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.services.run_tools import tool_failure_status
from servicedesk_ai.worker import poll


@pytest.mark.parametrize("process", ["worker", "receiver"])
def test_module_entrypoint_logs_reach_application_handler(process, monkeypatch):
    """真实入口以__main__执行时仍写应用日志，不能因仅按模块导入测试而漏掉故障日志。"""
    captured = []

    class Sink(logging.Handler):
        """收集应用处理器实际收到的JSON，不依赖根logger或控制台兜底。"""

        def emit(self, record):
            captured.append(json.loads(record.getMessage()))

    def skip_external_start(coroutine):
        """只拦截最外层事件循环启动，入口模块和日志装配关系使用真实代码。"""
        coroutine.close()

    monkeypatch.setattr(asyncio, "run", skip_external_start)
    logger = logging.getLogger("servicedesk_ai")
    original_level = logger.level
    handler = BoundedLogHandler(Sink(), JsonFormatter("test", process))
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    try:
        path = Path(__file__).resolve().parents[1] / "src/servicedesk_ai" / f"{process}.py"
        namespace = runpy.run_path(str(path), run_name="__main__")
        namespace["LOGGER"].warning("入口故障日志探针")
        handler.records.join()
        assert len(captured) == 1
        assert captured[0]["process"] == process
        assert captured[0]["message"] == "入口故障日志探针"
    finally:
        logger.removeHandler(handler)
        logger.setLevel(original_level)
        handler.close()


@pytest.mark.asyncio
@pytest.mark.parametrize("message_id", ["A" * 32, "unsafe\npassword=private-value"])
async def test_receiver_invalid_envelope_is_unacknowledged_and_logs_safe_identity(
    message_id, monkeypatch, caplog
):
    """坏信封不ACK，重试日志可关联合法消息身份，但不回显正文、凭据或恶意标识。"""
    consumer = Mock()
    message = SimpleNamespace(
        body=b'{"schemaVersion":999,"debug":"private-payload"}',
        message_id=message_id,
        delivery_attempt=3,
    )
    consumer.receive.side_effect = [[message], asyncio.CancelledError()]

    @asynccontextmanager
    async def resources(settings, *, process):
        """协议拒绝发生在业务事务之前，不需要伪造数据库成功或连接外部组件。"""
        yield SimpleNamespace(database=object())

    monkeypatch.setattr(receiver, "get_settings", lambda: Settings(infrastructure_enabled=False))
    monkeypatch.setattr(receiver, "ClientConfiguration", Mock())
    monkeypatch.setattr(receiver, "Credentials", Mock())
    monkeypatch.setattr(receiver, "SimpleConsumer", Mock(return_value=consumer))
    monkeypatch.setattr(receiver, "open_resources", resources)
    monkeypatch.setattr(receiver.asyncio, "sleep", AsyncMock())
    with caplog.at_level(logging.WARNING, logger="servicedesk_ai.receiver"):
        with pytest.raises(asyncio.CancelledError):
            await receiver.serve()
    consumer.ack.assert_not_called()
    consumer.shutdown.assert_called_once()
    records = [r.getMessage() for r in caplog.records if r.name == "servicedesk_ai.receiver"]
    assert len(records) == 1
    expected = message_id if message_id == "A" * 32 else "unknown"
    assert f"messageId={expected}" in records[0]
    assert "deliveryAttempt=3" in records[0]
    assert "errorType=ValidationError" in records[0]
    assert "private-payload" not in records[0] and "private-value" not in records[0]


@pytest.mark.asyncio
async def test_worker_reconnects_with_bounded_backoff_and_preserves_cancellation(monkeypatch):
    """只重新进入持久领取，不重新调用旧Run；成功恢复后退避重置。"""
    failure = OperationalError("hidden SQL", {}, RuntimeError("hidden credentials"))
    operation = AsyncMock(side_effect=[failure, failure, True, failure, asyncio.CancelledError()])
    sleeper = AsyncMock()
    monkeypatch.setattr("servicedesk_ai.worker.asyncio.sleep", sleeper)
    with pytest.raises(asyncio.CancelledError):
        await poll(operation, "run", 1)
    assert [call.args[0] for call in sleeper.call_args_list] == [1, 2, 1]
    assert operation.await_count == 5


@pytest.mark.parametrize(
    "error,status",
    [
        (BusinessError(CommonErrorCode.FORBIDDEN), "FORBIDDEN"),
        (BusinessError(CommonErrorCode.INVALID_REQUEST), "INVALID_ARGUMENT"),
        (BusinessError(CommonErrorCode.CONFLICT), "STALE_CONTEXT"),
        (BusinessError(CommonErrorCode.DEPENDENCY_TIMEOUT), "DEPENDENCY_FAILED"),
        (RuntimeError("unknown"), "UNKNOWN"),
        (asyncio.CancelledError(), "CANCELLED"),
    ],
)
def test_known_tool_rejection_is_distinct_from_unknown_outcome(error, status):
    """只有确定状态进入安全工具反馈，取消和环境变化不会被吞成空结果。"""
    assert tool_failure_status(error) == status


def test_async_log_queue_is_bounded_and_keeps_producer_trace():
    """慢磁盘不会阻塞业务；丢弃计数可见，后台线程保留原span而非读取空上下文。"""
    entered, release = Event(), Event()
    events = []

    class Sink(logging.Handler):
        """模拟短暂阻塞的日志磁盘，不连接外部采集器。"""

        def emit(self, record):
            entered.set()
            assert release.wait(3)
            events.append(json.loads(record.getMessage()))

    provider = TracerProvider()
    handler = BoundedLogHandler(Sink(), JsonFormatter("test", "worker"), capacity=1)
    logger = logging.getLogger("runtime-recovery-test")
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    try:
        with provider.get_tracer("test").start_as_current_span("call") as span:
            expected = format(span.get_span_context().trace_id, "032x")
            logger.info("first")
            assert entered.wait(2)
            logger.info("pending")
            logger.info("discarded")
            assert handler.dropped == 1
        release.set()
        handler.records.join()
        logger.info("recovered")
        handler.records.join()
        assert events[0]["traceId"] == expected
        assert events[0]["process"] == "worker"
        assert events[-1]["droppedLogs"] == 1
        assert [event["message"] for event in events] == ["first", "pending", "recovered"]
    finally:
        release.set()
        logger.removeHandler(handler)
        handler.close()
        provider.shutdown()


def test_log_identifiers_are_whitelisted_without_serializing_business_payloads():
    """日志关联任务与运行，但拒绝正文、凭据以及含换行的伪造标识。"""
    record = logging.makeLogRecord(
        {
            "msg": "运行完成",
            "levelname": "INFO",
            "runId": "a" * 32,
            "taskId": "b" * 32,
            "event": "run_completed",
            "durationMs": 12,
            "ticketId": "unsafe\nvalue",
            "payload": "private",
            "password": "secret",
        }
    )
    output = json.loads(JsonFormatter("test", "worker").format(record))
    assert output["runId"] == "a" * 32 and output["taskId"] == "b" * 32
    assert output["durationMs"] == 12
    assert not {"payload", "password", "ticketId"} & output.keys()
