"""按环境装配API入口配置，不预建尚无消费者的外部客户端。"""

from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """集中校验进程入口与基础环境，生产默认不开放文档。"""

    model_config = SettingsConfigDict(env_prefix="DESK_AI_", extra="forbid")

    environment: Literal["dev", "prod"] = Field(default="dev", description="唯一基础环境")
    http_host: str = Field(default="127.0.0.1", min_length=1, description="HTTP监听地址")
    http_port: int = Field(default=28105, ge=1, le=65535, description="HTTP监听端口")


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """在进程内复用同一份已验证配置。"""
    return Settings()
