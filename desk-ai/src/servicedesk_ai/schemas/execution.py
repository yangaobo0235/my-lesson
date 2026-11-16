"""持久运行的固定配置及上下文快照；读取时校验，不作为当前授权。作者：yangaobo。"""

from typing import Literal

from pydantic import Field

from servicedesk_ai.bootstrap.components import ExecutionSettings
from servicedesk_ai.schemas.diagnosis import SupportEnvironment
from servicedesk_ai.schemas.indexing import WireModel


class GenerationPipeline(ExecutionSettings):
    """受理时固定的执行预算及提示词、工具、模型版本。"""

    schema_version: Literal[1]
    prompt_version: str
    tool_version: str
    model: str


class GenerationContext(WireModel):
    """原绑定和环境快照，来源列表只记录真实已使用标识。"""

    schema_version: Literal[1]
    mode: Literal["CUSTOMER_SELF_SERVICE", "TECHNICIAN_ASSIST"]
    customer_id: str
    service_item_id: str | None
    device_id: str | None
    product_model_id: str | None
    ticket_id: str | None
    environment: SupportEnvironment
    source_usage_ids: list[str] = Field(default_factory=list)
    ticket_title: str
    ticket_symptom: str
