"""定义AI所属外部资源配置；凭据隐藏，启用时由入口统一验证。"""

from typing import Literal
from urllib.parse import urlsplit

from pydantic import BaseModel, ConfigDict, Field, SecretStr, field_validator


class ComponentSettings(BaseModel):
    """拒绝配置拼写错误，并避免校验异常回显原始密码。"""

    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True)


class MySqlSettings(ComponentSettings):
    """AI独立数据库、连接池及结构迁移凭据。"""

    host: str = "192.168.23.66"
    port: int = Field(default=3306, ge=1, le=65535)
    database: str = "desk_ai_test"
    username: str = "desk_ai_test"
    password: SecretStr = SecretStr("")
    migration_username: str = ""
    migration_password: SecretStr = SecretStr("")
    pool_size: int = Field(default=5, ge=1, le=10)


class RedisSettings(ComponentSettings):
    """AI每日次数资源，和登录防刷、上传互斥分开命名。"""

    host: str = "192.168.23.66"
    port: int = Field(default=6379, ge=1, le=65535)
    username: str = "default"
    password: SecretStr = SecretStr("")
    database: int = Field(default=0, ge=0, le=15)
    key_prefix: str = "desk:test:ai:"
    daily_limit: int = Field(default=20, ge=1)


class HttpSettings(ComponentSettings):
    """供应商服务根地址及TLS信任；禁止地址中夹带凭据。"""

    url: str
    ca_file: str | None = None
    timeout_seconds: float = Field(default=5, gt=0, le=120)

    @field_validator("url")
    @classmethod
    def validate_origin(cls, value: str) -> str:
        """仅允许HTTP(S)根地址，错误中不附带输入。"""
        parsed = urlsplit(value)
        if (
            parsed.scheme not in {"http", "https"}
            or not parsed.hostname
            or parsed.username is not None
            or parsed.password is not None
            or parsed.query
            or parsed.fragment
            or parsed.path not in {"", "/"}
        ):
            raise ValueError("必须使用无凭据、路径及查询串的HTTP(S)根地址")
        return value.rstrip("/")


class ElasticsearchSettings(HttpSettings):
    """独立知识检索索引，不使用ELK日志写入账号。"""

    url: str = "http://192.168.23.66:9200"
    username: str = "desk_ai_test"
    password: SecretStr = SecretStr("")
    index: str = "desk_chunks_test_te4_1024_v1"


class QdrantSettings(HttpSettings):
    """固定模型空间的1024维余弦检索配置。"""

    url: str = "http://192.168.23.66:6333"
    api_key: SecretStr = SecretStr("")
    collection: str = "desk_chunks_test_te4_1024_v1"
    dimensions: int = Field(default=1024, ge=1024, le=1024)
    distance: Literal["Cosine"] = "Cosine"
    exact: Literal[True] = True


class MineruSettings(HttpSettings):
    """独立MinerU V1 Tasks服务，不向其安装本进程模型依赖。"""

    url: str = "http://192.168.23.66:28000"
    timeout_seconds: float = Field(default=120, gt=0, le=120)
    api_key: SecretStr = SecretStr("")
    language: str = "ch"
    max_file_bytes: Literal[20971520] = 20971520


class ModelSettings(ComponentSettings):
    """百炼兼容协议配置，模型及向量空间遵守固定合同。"""

    base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    api_key: SecretStr = SecretStr("")
    chat_model: str = "qwen3.8-flash"
    embedding_model: Literal["text-embedding-v4"] = "text-embedding-v4"
    dimensions: int = Field(default=1024, ge=1024, le=1024)
    embedding_batch_size: int = Field(default=10, ge=1, le=10)
    vector_space: Literal["te4_1024_v1"] = "te4_1024_v1"
    temperature: float = Field(default=0.2, ge=0, le=2)
    max_output_tokens: int = Field(default=2048, ge=1, le=8192)
    enable_thinking: Literal[False] = False
    timeout_seconds: float = Field(default=60, gt=0, le=120)

    @field_validator("base_url")
    @classmethod
    def validate_model_url(cls, value: str) -> str:
        """模型密钥只能发送至显式配置的HTTPS服务，不跟随重定向。"""
        parsed = urlsplit(value)
        if (
            parsed.scheme != "https"
            or not parsed.hostname
            or parsed.username is not None
            or parsed.password is not None
            or parsed.query
            or parsed.fragment
        ):
            raise ValueError("模型地址必须是无凭据及查询串的HTTPS地址")
        return value.rstrip("/")


class RocketMqSettings(ComponentSettings):
    """仅由独立receiver消费已提交的事务消息。"""

    endpoints: str = "192.168.23.66:8081"
    access_key: SecretStr = SecretStr("")
    secret_key: SecretStr = SecretStr("")
    tls_enabled: bool = False
    topic: str = "desk-knowledge-index-command-test"
    consumer_group: str = "desk-ai-index-command-test"
    invisible_seconds: int = Field(default=30, ge=10, le=60)
    acceptance_timeout_seconds: int = Field(default=5, ge=1, le=10)


class RpcSettings(ComponentSettings):
    """跨语言内部端口与双向TLS文件，缺失时不回退明文。"""

    original_directory: str = "../.local/runtime/originals"

    user_target: str = "localhost:26202"
    ticket_target: str = "localhost:26203"
    knowledge_target: str = "localhost:26204"
    listen_address: str = "127.0.0.1:26205"
    ca_file: str = ""
    certificate_file: str = ""
    private_key_file: str = ""
    service_token: SecretStr = SecretStr("")
    knowledge_token: SecretStr = SecretStr("")
    ticket_token: SecretStr = SecretStr("")
    max_message_bytes: Literal[4194304] = 4194304


class ExecutionSettings(ComponentSettings):
    """索引与生成的独立执行预算，不由Sentinel代替。"""

    index_concurrency: int = Field(default=1, ge=1, le=4)
    generation_concurrency: int = Field(default=2, ge=1, le=4)
    queue_capacity: int = Field(default=100, ge=1, le=100)
    lease_seconds: int = Field(default=60, ge=30, le=120)
    renewal_seconds: int = Field(default=15, ge=5, le=20)
    total_seconds: int = Field(default=180, ge=1, le=180)
    max_model_calls: int = Field(default=8, ge=1, le=8)
    max_tool_calls: int = Field(default=12, ge=1, le=12)
    max_input_tokens: int = Field(default=12000, ge=1, le=12000)
    max_output_tokens: int = Field(default=2048, ge=1, le=2048)
    total_tokens: int = Field(default=32000, ge=1, le=32000)
    readonly_tool_concurrency: int = Field(default=4, ge=1, le=4)
    tool_timeout_seconds: int = Field(default=10, ge=1, le=10)
    source_limit: int = Field(default=64, ge=1, le=64)
    facts_per_round: int = Field(default=30, ge=1, le=30)
    candidates_per_source: Literal[50] = 50
    rrf_k: Literal[60] = 60
    evidence_limit: Literal[8] = 8


class ObservabilitySettings(HttpSettings):
    """有界Zipkin投递及本机滚动JSON日志。"""

    url: str = "http://192.168.23.66:9411"
    sample_ratio: float = Field(default=0.1, ge=0, le=1)
    timeout_seconds: float = Field(default=2, gt=0, le=5)
    log_directory: str = "../.local/runtime/logs/desk-ai"
