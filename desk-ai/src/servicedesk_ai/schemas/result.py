"""维护普通JSON结果，文件、健康、OpenAPI及RPC不使用该包装。"""

from pydantic import BaseModel, ConfigDict, Field


class Result[T](BaseModel):
    """对齐code/message/data/traceId，保留无数据及失败时的null。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid", frozen=True)

    code: int = Field(description="唯一成功为0，错误使用已登记非零编号")
    message: str = Field(description="安全中文说明")
    data: T | None = Field(default=None, description="有效载荷，无数据或失败时为null")
    trace_id: str = Field(alias="traceId", description="当前请求关联标识")
