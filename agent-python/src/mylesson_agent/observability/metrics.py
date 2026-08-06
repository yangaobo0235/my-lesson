from prometheus_client import Counter, Gauge, Histogram

REQUESTS = Counter("agent_request_total", "Agent requests", ["operation", "status"])
REQUEST_LATENCY = Histogram(
    "agent_request_duration_seconds", "Agent request latency", ["operation"]
)
MODEL_CALLS = Counter("agent_model_call_total", "Model calls", ["model", "status"])
MODEL_LATENCY = Histogram("agent_model_latency_seconds", "Model call latency", ["model"])
MODEL_TOKENS = Counter("agent_model_tokens_total", "Model tokens", ["model", "kind"])
TOOL_CALLS = Counter("agent_tool_call_total", "Tool calls", ["tool", "status"])
SEARCH_BACKEND_CALLS = Counter(
    "agent_search_backend_call_total", "Search backend calls", ["backend", "operation", "status"]
)
SEARCH_BACKEND_LATENCY = Histogram(
    "agent_search_backend_duration_seconds", "Search backend latency", ["backend", "operation"]
)
RETRIEVAL_NO_ANSWER = Counter("agent_retrieval_no_answer_total", "RAG no-answer decisions")
SSE_CONNECTIONS = Gauge("agent_sse_connections", "Open SSE connections")
LOCK_CONFLICTS = Counter("agent_conversation_lock_conflict_total", "Conversation lock conflicts")
