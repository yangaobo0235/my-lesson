"""创建有界Zipkin投递和滚动JSON日志，不记录正文、请求头及模型输入。"""

import json
import logging
import math
import os
import re
from datetime import UTC, datetime
from logging.handlers import QueueHandler, QueueListener, RotatingFileHandler
from pathlib import Path
from queue import Empty, Full, Queue
from typing import Literal

from opentelemetry import trace
from opentelemetry.exporter.zipkin.json import ZipkinExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.trace.sampling import ParentBased, TraceIdRatioBased

from servicedesk_ai.bootstrap.settings import Settings


class JsonFormatter(logging.Formatter):
    """输出Logstash可解析的一行JSON，仅写入受控应用日志字段。"""

    def __init__(self, environment: str, process: str) -> None:
        """固定资源环境，避免日志依赖可变全局配置。"""
        super().__init__()
        self.environment = environment
        self.process = process

    def format(self, record: logging.LogRecord) -> str:
        """关联当前span；异常仅写类型，完整敏感载荷不进入普通日志。"""
        span = trace.get_current_span().get_span_context()
        event = {
            "@timestamp": datetime.now(UTC).isoformat(),
            "service": "desk-ai",
            "process": self.process,
            "processId": os.getpid(),
            "instance": self.process + "-" + str(os.getpid()),
            "environment": self.environment,
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "traceId": format(span.trace_id, "032x") if span.is_valid else None,
            "spanId": format(span.span_id, "016x") if span.is_valid else None,
            "droppedLogs": getattr(record, "dropped_logs", 0),
        }
        if record.exc_info and record.exc_info[0]:
            event["exceptionType"] = record.exc_info[0].__name__
        for field in ("event", "runId", "taskId", "jobId", "ticketId", "stage", "errorCode"):
            value = getattr(record, field, None)
            if isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_.:-]{1,128}", value):
                event[field] = value
        duration = getattr(record, "durationMs", None)
        if isinstance(duration, (int, float)) and math.isfinite(duration) and duration >= 0:
            event["durationMs"] = duration
        return json.dumps(event, ensure_ascii=False)


class BoundedLogHandler(QueueHandler):
    """在调用线程固定追踪标识，后台写文件；队列满仅丢日志，不阻塞业务。"""

    def __init__(
        self, sink: logging.Handler, formatter: logging.Formatter, capacity: int = 512
    ) -> None:
        """文件和消费者线程均由当前实例拥有，关闭先摘除入口再冲刷。"""
        self.records: Queue[logging.LogRecord] = Queue(capacity)
        super().__init__(self.records)
        self.setFormatter(formatter)
        self.sink = sink
        self.dropped = 0
        self.consumer = QueueListener(self.records, sink)
        self.consumer.start()
        self.stopped = False

    def emit(self, record: logging.LogRecord) -> None:
        """prepare在原调用上下文生成安全JSON，不在监听线程读取错误的span。"""
        if self.stopped:
            return
        record.dropped_logs = self.dropped
        try:
            self.records.put_nowait(self.prepare(record))
        except Full:
            self.dropped += 1

    def close(self) -> None:
        """只停止一次；满队列腾出终止槽，避免QueueListener.stop抛Full。"""
        self.acquire()
        try:
            if not self.stopped:
                self.stopped = True
                if self.records.full():
                    try:
                        self.records.get_nowait()
                    except Empty:
                        pass
                    else:
                        self.records.task_done()
                        self.dropped += 1
                self.consumer.stop()
                self.sink.close()
        finally:
            self.release()
            super().close()


def open_observability(
    settings: Settings,
    process: Literal["api", "worker", "receiver"] = "api",
) -> tuple[TracerProvider, logging.Handler]:
    """实例化当前进程的追踪及文件处理器，不设置不可恢复的全局Provider。"""
    config = settings.observability
    directory = Path(config.log_directory) / process
    directory.mkdir(parents=True, exist_ok=True)
    sink = RotatingFileHandler(
        directory / "application.json",
        maxBytes=20 * 1024 * 1024,
        backupCount=2,
        encoding="utf-8",
    )
    handler = BoundedLogHandler(sink, JsonFormatter(settings.resource_env, process))
    logger = logging.getLogger("servicedesk_ai")
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    provider = TracerProvider(
        resource=Resource.create(
            {
                "service.name": "desk-ai",
                "service.instance.id": process + "-" + str(os.getpid()),
                "process.role": process,
                "deployment.environment.name": settings.resource_env,
            }
        ),
        sampler=ParentBased(TraceIdRatioBased(config.sample_ratio)),
    )
    exporter = ZipkinExporter(
        endpoint=config.url + "/api/v2/spans", timeout=math.ceil(config.timeout_seconds)
    )
    # 内部采集端明确直连；Session仍由exporter关闭，不继承系统代理或netrc认证。
    exporter.session.trust_env = False
    provider.add_span_processor(
        BatchSpanProcessor(
            exporter,
            max_queue_size=512,
            max_export_batch_size=64,
            schedule_delay_millis=5000,
            export_timeout_millis=2000,
        )
    )
    return provider, handler
