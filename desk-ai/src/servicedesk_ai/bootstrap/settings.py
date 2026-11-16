"""集中绑定应用配置；开发显式读取当前目录.env，生产仅接收进程配置。"""

import os
from functools import lru_cache
from typing import Literal, Self

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from servicedesk_ai.bootstrap.components import (
    ElasticsearchSettings,
    ExecutionSettings,
    MineruSettings,
    ModelSettings,
    MySqlSettings,
    ObservabilitySettings,
    QdrantSettings,
    RedisSettings,
    RocketMqSettings,
    RpcSettings,
)


class Settings(BaseSettings):
    """集中校验进程入口与基础环境，生产默认不开放文档。"""

    model_config = SettingsConfigDict(
        env_prefix="DESK_AI_",
        env_nested_delimiter="__",
        extra="forbid",
        hide_input_in_errors=True,
        nested_model_default_partial_update=True,
    )

    environment: Literal["dev", "prod"] = Field(default="dev", description="唯一基础环境")
    http_host: str = Field(default="127.0.0.1", min_length=1, description="HTTP监听地址")
    http_port: int = Field(default=26105, ge=1, le=65535, description="HTTP监听端口")

    infrastructure_enabled: bool = True
    migrations_enabled: bool = True
    resource_env: Literal["test", "ci", "prod"] = "test"
    mysql: MySqlSettings = Field(default_factory=MySqlSettings)
    redis: RedisSettings = Field(default_factory=RedisSettings)
    elasticsearch: ElasticsearchSettings = Field(default_factory=ElasticsearchSettings)
    qdrant: QdrantSettings = Field(default_factory=QdrantSettings)
    mineru: MineruSettings = Field(default_factory=MineruSettings)
    model: ModelSettings = Field(default_factory=ModelSettings)
    rocketmq: RocketMqSettings = Field(default_factory=RocketMqSettings)
    rpc: RpcSettings = Field(default_factory=RpcSettings)
    execution: ExecutionSettings = Field(default_factory=ExecutionSettings)
    observability: ObservabilitySettings = Field(default_factory=ObservabilitySettings)

    @model_validator(mode="after")
    def validate_model_budget(self) -> Self:
        """单次输出与调用超时不能超过所声明的执行上限。"""
        if self.model.max_output_tokens > self.execution.max_output_tokens:
            raise ValueError("模型输出配置超过执行预算")
        if self.model.timeout_seconds > self.execution.total_seconds:
            raise ValueError("模型调用超时超过运行总预算")
        return self

    @model_validator(mode="after")
    def validate_connected_resources(self) -> Self:
        """正常启动要求完整凭据及一致空间，测试可显式隔离外部资源。"""
        if not self.infrastructure_enabled:
            return self
        required = {
            "mysql.password": self.mysql.password.get_secret_value(),
            "redis.password": self.redis.password.get_secret_value(),
            "elasticsearch.password": self.elasticsearch.password.get_secret_value(),
            "qdrant.api_key": self.qdrant.api_key.get_secret_value(),
            "model.api_key": self.model.api_key.get_secret_value(),
            "rocketmq.access_key": self.rocketmq.access_key.get_secret_value(),
            "rocketmq.secret_key": self.rocketmq.secret_key.get_secret_value(),
        }
        missing = [key for key, value in required.items() if not value.strip()]
        if missing:
            raise ValueError("缺少配置：" + ", ".join(missing))
        expected = "prod" if self.environment == "prod" else self.resource_env
        if expected != self.resource_env or (self.environment == "dev" and expected == "prod"):
            raise ValueError("基础环境与资源环境不一致")
        space = f"desk_chunks_{expected}_te4_1024_v1"
        if (
            self.mysql.database != f"desk_ai_{expected}"
            or self.redis.key_prefix != f"desk:{expected}:ai:"
            or self.elasticsearch.index != space
            or self.qdrant.collection != space
            or self.rocketmq.topic != f"desk-knowledge-index-command-{expected}"
            or self.rocketmq.consumer_group != f"desk-ai-index-command-{expected}"
        ):
            raise ValueError("数据库、Redis、检索空间或消息资源跨环境")
        return self


def load_settings(*, validate_infrastructure: bool = True) -> Settings:
    """按进程环境选择开发文件，数据库维护入口可独立于其他组件凭据加载。

    开发工作目录为desk-ai，文件使用Pydantic Settings原生dotenv源；
    DESK_AI_ENVIRONMENT=prod必须由进程环境提供，生产不读取本机文件。
    直接构造Settings不读取文件，便于测试及显式资源装配保持隔离。
    """
    environment = os.environ.get("DESK_AI_ENVIRONMENT", "dev")
    env_file = ".env" if environment == "dev" else None
    if validate_infrastructure:
        settings = Settings(_env_file=env_file, _env_file_encoding="utf-8")
    else:
        settings = Settings(
            _env_file=env_file, _env_file_encoding="utf-8", infrastructure_enabled=False
        )
    if settings.environment != environment:
        raise ValueError("基础环境须由进程环境选择，不能通过开发.env切换生产")
    return settings


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """复用当前进程已经验证的完整配置。"""
    return load_settings()
