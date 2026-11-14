"""在HTTP入口维护请求关联标识，不信任浏览器提交的追踪身份。"""

from contextlib import nullcontext
from uuid import uuid4

from opentelemetry import trace
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator
from starlette.types import ASGIApp, Message, Receive, Scope, Send


class RequestTraceMiddleware:
    """以原生ASGI包装保留流式响应与错误处理的请求标识。"""

    def __init__(self, app: ASGIApp) -> None:
        """保留所属应用，不建立共享请求状态。"""
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        """为HTTP响应附加当前请求标识，其他协议原样交付。"""
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        resources = getattr(scope["app"].state, "resources", None)
        span_context = nullcontext()
        if resources is not None:
            carrier = {
                name.decode("latin-1"): value.decode("latin-1")
                for name, value in scope.get("headers", [])
                if name == b"traceparent"
            }
            context = TraceContextTextMapPropagator().extract(carrier)
            span_context = resources.tracer_provider.get_tracer(__name__).start_as_current_span(
                "ai.http",
                context=context,
                kind=trace.SpanKind.SERVER,
                record_exception=False,
                set_status_on_exception=False,
            )

        async def send_with_trace(message: Message) -> None:
            """在响应开始时附加标识，不读取或改写响应正文。"""
            if message["type"] == "http.response.start":
                headers = [
                    (name, value)
                    for name, value in message.get("headers", [])
                    if name.lower() != b"x-trace-id"
                ]
                headers.append((b"x-trace-id", trace_id.encode("ascii")))
                message = {**message, "headers": headers}
            await send(message)

        with span_context:
            current = trace.get_current_span().get_span_context()
            trace_id = format(current.trace_id, "032x") if current.is_valid else uuid4().hex
            scope.setdefault("state", {})["trace_id"] = trace_id
            await self.app(scope, receive, send_with_trace)
