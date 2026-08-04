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

    delegation_secret: SecretStr | None = Field(
        default=None,
        validation_alias="AI_DELEGATION_SECRET",
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
    model_timeout_seconds: float = 45.0
    router_timeout_seconds: float = 10.0
    intent_confidence_threshold: float = 0.65
    max_model_calls: int = 6
    max_tool_calls: int = 8
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

    vector_dimensions: int = 1024
    vector_top_k: int = 12
    keyword_top_k: int = 12
    answer_top_n: int = 6
    minimum_relevant_score: float = 0.45
    rerank_minimum_score: float = 0.45

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
        return _valid_secret(self.delegation_secret) and _valid_secret(self.service_token)


@lru_cache
def get_settings() -> Settings:
    return Settings()
