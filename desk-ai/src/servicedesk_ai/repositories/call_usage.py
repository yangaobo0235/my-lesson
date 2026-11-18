"""模型账目聚合；未知计量保留原预留，不把新Run当成预算重置。作者：yangaobo。"""

from dataclasses import dataclass
from decimal import Decimal

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncConnection

from servicedesk_ai.errors.common import BusinessError
from servicedesk_ai.errors.run import RunErrorCode
from servicedesk_ai.schemas.tasks import TaskBudget


@dataclass(frozen=True)
class CallUsage:
    """保守预算占用与供应商计量分开；unknown_cost表示无法按金额放行。"""

    calls: int
    tokens: int
    cost: Decimal
    unknown_cost: bool
    unsettled: bool = False


async def usage(
    db: AsyncConnection, *, task_id: str | None = None, run_id: str | None = None
) -> CallUsage:
    """调用方持任务或Run锁；只允许一个明确聚合范围。"""
    if (task_id is None) == (run_id is None):
        raise ValueError("必须指定唯一账目范围")
    condition = "r.task_id=:identifier" if task_id else "r.id=:identifier"
    row = (
        (
            await db.execute(
                text(f"""
        SELECT COUNT(*) AS calls,
          COALESCE(SUM(CASE WHEN c.status='CANCELLED' THEN 0
            WHEN c.input_tokens IS NOT NULL AND c.output_tokens IS NOT NULL
              THEN c.input_tokens+c.output_tokens ELSE c.reserved_tokens END),0) AS tokens,
          COALESCE(SUM(CASE WHEN c.status='CANCELLED' THEN 0
            ELSE COALESCE(c.estimated_cost,c.reserved_cost,0) END),0) AS cost,
          COALESCE(MAX(c.status<>'CANCELLED' AND c.estimated_cost IS NULL
              AND c.reserved_cost IS NULL),0) AS unknown_cost,
          COALESCE(MAX(c.status IN ('PREPARED','IN_FLIGHT','UNKNOWN')
            OR c.input_tokens IS NULL OR c.output_tokens IS NULL),0) AS unsettled
        FROM ai_model_call c JOIN ai_run r ON r.id=c.run_id
        WHERE {condition} AND c.status<>'CANCELLED'
    """),
                {"identifier": task_id or run_id},
            )
        )
        .mappings()
        .one()
    )
    return CallUsage(
        int(row["calls"]),
        int(row["tokens"]),
        Decimal(row["cost"]),
        bool(row["unknown_cost"]),
        bool(row["unsettled"]),
    )


def require_budget(
    value: CallUsage,
    policy: TaskBudget,
    *,
    tokens: int = 1,
    cost: Decimal | None = None,
    calls: int = 1,
) -> None:
    """受理和每次付费意图都检查累计占用，达到限额不能靠新请求绕过。"""
    exhausted = (
        (calls > 0 and value.unsettled)
        or value.calls + calls > policy.max_model_calls
        or value.tokens + tokens > policy.max_tokens
    )
    if policy.max_estimated_cost is not None:
        exhausted |= (
            value.unknown_cost or cost is None or (value.cost + cost > policy.max_estimated_cost)
        )
    if exhausted:
        raise BusinessError(RunErrorCode.TASK_BUDGET_EXHAUSTED)


async def interrupt_calls(
    db: AsyncConnection, *, run_id: str | None = None, index_job_id: str | None = None
) -> None:
    """父任务取消或租约接管时原子收敛未结调用；未发送为零，在途保持未知。"""
    if (run_id is None) == (index_job_id is None):
        raise ValueError("必须指定唯一父任务")
    column = "run_id" if run_id else "index_job_id"
    parameters = {"id": run_id or index_job_id}
    await db.execute(
        text(f"""
        UPDATE ai_model_call SET status='CANCELLED',input_tokens=0,output_tokens=0,
            cached_input_tokens=0,estimated_cost=0,error_code='PARENT_STOPPED_BEFORE_SEND',
            finished_at=UTC_TIMESTAMP(6) WHERE {column}=:id AND status='PREPARED'
    """),
        parameters,
    )
    await db.execute(
        text(f"""
        UPDATE ai_model_call SET status='UNKNOWN',error_code='PARENT_STOPPED_AFTER_SEND',
            finished_at=UTC_TIMESTAMP(6) WHERE {column}=:id AND status='IN_FLIGHT'
    """),
        parameters,
    )
