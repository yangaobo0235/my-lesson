"""逐次模型调用的计价与输入清单；不持久化完整Prompt或隐藏推理。作者：yangaobo。"""

from decimal import Decimal
from typing import Literal, Self

from pydantic import Field, model_validator

from servicedesk_ai.schemas.indexing import Digest, Identifier, WireModel


class PricingSnapshot(WireModel):
    """费率以每百万Token计，未知价保持空值，不能用零价伪装已知成本。"""

    schema_version: Literal[1] = 1
    currency: Literal["CNY", "USD"] = "CNY"
    policy_version: str = Field(default="unconfigured", min_length=1, max_length=64)
    input_per_million: Decimal | None = Field(default=None, ge=0)
    output_per_million: Decimal | None = Field(default=None, ge=0)
    cached_input_per_million: Decimal | None = Field(default=None, ge=0)

    @model_validator(mode="after")
    def complete_tariff(self) -> Self:
        """输入/输出单价同空同有，缓存单价不能超过普通输入单价。"""
        if (self.input_per_million is None) != (self.output_per_million is None):
            raise ValueError("输入输出费率必须成对配置")
        if self.cached_input_per_million is not None and (
            self.input_per_million is None or self.cached_input_per_million > self.input_per_million
        ):
            raise ValueError("缓存费率缺少普通输入费率或超过其上限")
        return self

    def cost(self, inputs: int, outputs: int, cached: int = 0) -> Decimal | None:
        """已知费率才估算，缺少缓存价格时保守使用普通输入单价。"""
        if min(inputs, outputs, cached) < 0 or cached > inputs:
            raise ValueError("非法模型计量")
        if self.input_per_million is None or self.output_per_million is None:
            return None
        cached_rate = self.cached_input_per_million
        if cached_rate is None:
            cached_rate = self.input_per_million
        return (
            (
                Decimal(inputs - cached) * self.input_per_million
                + Decimal(cached) * cached_rate
                + Decimal(outputs) * self.output_per_million
            )
            / Decimal(1_000_000)
        ).quantize(Decimal("0.00000001"))


class ModelInputManifest(WireModel):
    """请求指纹与完整性用于恢复判定，来源标识只能由宿主核验后写入。"""

    schema_version: Literal[1] = 1
    request_sha256: Digest
    estimator_version: str
    input_tokens_upper: int = Field(ge=0)
    output_tokens_limit: int = Field(ge=0)
    source_usage_ids: list[Identifier] = Field(default_factory=list, max_length=64)
    review_of_call_id: Identifier | None = Field(
        default=None, description="依据核验对应的本轮候选回答调用；普通生成和Embedding为空"
    )
    complete: Literal[True] = True
