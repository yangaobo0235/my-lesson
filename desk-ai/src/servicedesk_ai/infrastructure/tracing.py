"""有界W3C传输上下文；不参与业务摘要或授权，也不保存baggage。作者：yangaobo。"""

import re
from collections.abc import Mapping

from opentelemetry import context, trace
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator

PROPAGATOR = TraceContextTextMapPropagator()


def parent(value: Mapping[str, object] | None) -> context.Context:
    """拒绝超长或非法上下文，回到无父新链而不信任任意调用身份。"""
    value = value or {}
    header = value.get("traceparent")
    if not isinstance(header, str) or not re.fullmatch(
        r"[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}", header
    ):
        return context.Context()
    carrier = {"traceparent": header}
    state = value.get("tracestate")
    if isinstance(state, str) and len(state) <= 512:
        carrier["tracestate"] = state
    result = PROPAGATOR.extract(carrier)
    return (
        result if trace.get_current_span(result).get_span_context().is_valid else context.Context()
    )


def snapshot() -> dict[str, object] | None:
    """只保存当前实际span的合法上下文，无span时保持null。"""
    carrier: dict[str, str] = {}
    PROPAGATOR.inject(carrier)
    if "traceparent" not in carrier:
        return None
    return {"schema_version": 1, **carrier}
