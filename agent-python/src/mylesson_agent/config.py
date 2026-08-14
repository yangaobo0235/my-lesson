import json
from functools import lru_cache

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

PLACEHOLDER_SECRETS = {
    "change-me",
    "generate-at-least-32-random-bytes",
    "generate-an-independent-service-token",
    "generate-the-same-value-as-ai-identity-secret",
}


def _valid_secret(secret: SecretStr | None, *, minimum_bytes: int = 32) -> bool:
    if secret is None:
        return False
    value = secret.get_secret_value().strip()
    return value not in PLACEHOLDER_SECRETS and len(value.encode("utf-8")) >= minimum_bytes


def _valid_public_keys(value: SecretStr | None) -> bool:
    if value is None:
        return False
    try:
        keys = json.loads(value.get_secret_value())
        return isinstance(keys, dict) and bool(keys) and all(
            isinstance(key_id, str)
            and bool(key_id.strip())
            and isinstance(public_key, str)
            and "BEGIN PUBLIC KEY" in public_key.replace("\\n", "\n")
            for key_id, public_key in keys.items()
        )
    except (TypeError, ValueError):
        return False


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        # Source runs start in agent-python/, while the shared local env lives
        # at the monorepo root. A service-local file can override shared values.
        env_file=("../.env", ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )

    app_name: str = "ml-agent-python"
    app_env: str = "dev"
    app_port: int = 24109
    log_level: str = "INFO"

    database_url: str = Field(
        default="postgresql+asyncpg://mylesson_agent:change-me@127.0.0.1:5432/mylesson_agent",
        validation_alias="AGENT_DATABASE_URL",
    )
    migration_database_url: str = Field(
        default="postgresql+psycopg://mylesson_agent:change-me@127.0.0.1:5432/mylesson_agent",
        validation_alias="AGENT_MIGRATION_DATABASE_URL",
    )
    redis_url: str = "redis://127.0.0.1:6379/1"

    delegation_public_keys: SecretStr | None = Field(
        default=None,
        validation_alias="AI_DELEGATION_PUBLIC_KEYS",
    )
    service_token: SecretStr | None = Field(
        default=None,
        validation_alias="AI_INTERNAL_TOKEN",
    )
    delegation_issuer: str = "ml-gateway"
    delegation_audience: str = "ml-agent"

    dashscope_api_key: SecretStr | None = Field(
        default=None,
        validation_alias="DASHSCOPE_API_KEY",
    )
    model_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    chat_model: str = Field(default="qwen3-max", validation_alias="AI_CHAT_MODEL")
    router_model: str = Field(default="qwen-flash", validation_alias="AI_ROUTER_MODEL")
    embedding_model: str = Field(
        default="text-embedding-v4",
        validation_alias="AI_EMBEDDING_MODEL",
    )
    rerank_model: str = Field(default="qwen3-rerank", validation_alias="AI_RERANK_MODEL")
    rerank_url: str = (
        "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"
    )
    rerank_timeout_seconds: float = 6.0
    model_timeout_seconds: float = 45.0
    router_timeout_seconds: float = 10.0
    intent_confidence_threshold: float = 0.65
    max_model_calls: int = 6
    max_tool_calls: int = 8
    run_deadline_seconds: float = 60.0
    answer_delta_size: int = 48
    worker_poll_seconds: float = 0.5
    conversation_lock_ttl_seconds: int = 120
    worker_heartbeat_seconds: float = 15.0
    worker_stale_seconds: int = 60

    user_service_url: str = "http://127.0.0.1:24102"
    course_service_url: str = "http://127.0.0.1:24103"
    sale_service_url: str = "http://127.0.0.1:24104"
    order_service_url: str = "http://127.0.0.1:24105"
    tool_timeout_seconds: float = 12.0
    tool_read_max_attempts: int = 3
    tool_retry_base_seconds: float = 0.1

    search_service_url: str = Field(
        default="http://127.0.0.1:24111",
        validation_alias="AI_SEARCH_SERVICE_URL",
    )
    search_timeout_seconds: float = 8.0
    keyword_backend_enabled: bool = Field(
        default=True,
        validation_alias="AI_KEYWORD_BACKEND_ENABLED",
    )

    vector_dimensions: int = 1024
    vector_top_k: int = 20
    keyword_top_k: int = 20
    rrf_top_k: int = 20
    rrf_k: int = 60
    rrf_vector_weight: float = 1.0
    rrf_keyword_weight: float = 1.0
    answer_top_n: int = 6
    max_hits_per_source: int = 2
    vector_minimum_score: float = 0.45
    rerank_minimum_score: float = 0.45
    query_rewrite_enabled: bool = True

    otel_exporter_otlp_endpoint: str | None = None
    langfuse_host: str | None = None
    langfuse_public_key: str | None = None
    langfuse_secret_key: SecretStr | None = None

    @property
    def model_configured(self) -> bool:
        if self.dashscope_api_key is None:
            return False
        value = self.dashscope_api_key.get_secret_value().strip()
        return bool(value and value not in PLACEHOLDER_SECRETS)

    @property
    def security_configured(self) -> bool:
        return _valid_public_keys(self.delegation_public_keys) and _valid_secret(self.service_token)


@lru_cache
def get_settings() -> Settings:
    return Settings()
