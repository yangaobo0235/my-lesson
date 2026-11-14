"""创建有界Zipkin投递和滚动JSON日志，不记录正文、请求头及模型输入。"""

import json
import logging
from datetime import UTC, datetime
from logging.handlers import RotatingFileHandler
from pathlib import Path

from opentelemetry import trace
from opentelemetry.exporter.zipkin.json import ZipkinExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.trace.sampling import ParentBased, TraceIdRatioBased

from servicedesk_ai.bootstrap.settings import Settings


class JsonFormatter(logging.Formatter):
    """输出Logstash可解析的一行JSON，仅写入受控应用日志字段。"""

    def __init__(self, environment: str) -> None:
        """固定资源环境，避免日志依赖可变全局配置。"""
        super().__init__()
        self.environment = environment

    def format(self, record: logging.LogRecord) -> str:
        """关联当前span；异常仅写类型，完整敏感载荷不进入普通日志。"""
        span = trace.get_current_span().get_span_context()
        event = {
            "@timestamp": datetime.now(UTC).isoformat(),
            "service": "desk-ai",
            "environment": self.environment,
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "traceId": format(span.trace_id, "032x") if span.is_valid else None,
        }
        if record.exc_info and record.exc_info[0]:
            event["exceptionType"] = record.exc_info[0].__name__
        return json.dumps(event, ensure_ascii=False)


def open_observability(settings: Settings) -> tuple[TracerProvider, logging.Handler]:
    """实例化当前进程的追踪及文件处理器，不设置不可恢复的全局Provider。"""
    config = settings.observability
    directory = Path(config.log_directory)
    directory.mkdir(parents=True, exist_ok=True)
    handler = RotatingFileHandler(
        directory / "application.json",
        maxBytes=20 * 1024 * 1024,
        backupCount=2,
        encoding="utf-8",
    )
    handler.setFormatter(JsonFormatter(settings.resource_env))
    logger = logging.getLogger("servicedesk_ai")
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    provider = TracerProvider(
        resource=Resource.create(
            {
                "service.name": "desk-ai",
                "deployment.environment.name": settings.resource_env,
            }
        ),
        sampler=ParentBased(TraceIdRatioBased(config.sample_ratio)),
    )
    provider.add_span_processor(
        BatchSpanProcessor(
            ZipkinExporter(endpoint=config.url + "/api/v2/spans", timeout=config.timeout_seconds),
            max_queue_size=512,
            max_export_batch_size=64,
            schedule_delay_millis=5000,
            export_timeout_millis=2000,
        )
    )
    return provider, handler
