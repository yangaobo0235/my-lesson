"""模型及工具调用账目、引用与诊断终态的持久化；事务与授权由调用用例持有。作者：yangaobo。"""

from collections.abc import Mapping, Sequence

from sqlalchemy import text
from sqlalchemy.engine import Row, RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection


async def mark_model_result_unknown(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """标记模型结果未知，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                UPDATE ai_model_call SET status='UNKNOWN',error_code='MODEL_RESULT_UNKNOWN',
                finished_at=UTC_TIMESTAMP(6) WHERE id=:id AND status='STARTED'
            """),
        parameters,
    )


async def input_facts(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Sequence[RowMapping]:
    """读取输入事实，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text("""
                SELECT
                f.id,f.state_version,f.fact_kind,f.statement,f.source_type,f.source_locator FROM
                ai_diagnostic_fact f WHERE f.conversation_id=:id AND f.operation='ASSERT' AND
                NOT EXISTS (SELECT 1 FROM ai_diagnostic_fact s WHERE s.supersedes_fact_id=f.id)
                ORDER BY f.state_version,f.id LIMIT 31
            """),
                parameters,
            )
        )
        .mappings()
        .all()
    )


async def insert_model_call(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记模型调用，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                INSERT INTO ai_model_call
                (id,generation_id,call_number,purpose,status,provider,requested_model,input_manifest,started_at)
                VALUES
                (:id,:run,:number,'DECISION','STARTED','BAILIAN',:model,:manifest,UTC_TIMESTAMP(6))
            """),
        parameters,
    )


async def mark_first_model_use(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """标记首次模型使用，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                UPDATE ai_source_usage SET first_model_call_number=:number WHERE id=:id AND
                first_model_call_number IS NULL
            """),
        parameters,
    )


async def complete_model_call(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """提交成功模型调用，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                UPDATE ai_model_call SET status='SUCCEEDED',response_model=:model,
                input_tokens=:input,output_tokens=:output,cached_input_tokens=:cached,finished_at=UTC_TIMESTAMP(6)
                WHERE id=:id AND status='STARTED'
            """),
        parameters,
    )


async def fail_model_call(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记失败模型调用，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                UPDATE ai_model_call SET status='FAILED',error_code='INVALID_MODEL_RESULT',
                response_model=:model,input_tokens=:input,output_tokens=:output,
                cached_input_tokens=:cached,finished_at=UTC_TIMESTAMP(6) WHERE id=:id AND
                generation_id=:run AND status='STARTED'
            """),
        parameters,
    )


async def insert_tool_call(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记工具调用，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                INSERT INTO ai_tool_call
                (id,generation_id,tool_number,model_call_id,tool_name,arguments,result_refs,started_at)
                VALUES (:id,:run,:number,:call,:name,:args,:refs,UTC_TIMESTAMP(6))
            """),
        parameters,
    )


async def complete_tool_call(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """提交成功工具调用，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                UPDATE ai_tool_call SET status='SUCCEEDED',result_summary=:summary,
                result_refs=:refs,finished_at=UTC_TIMESTAMP(6) WHERE id=:id
            """),
        parameters,
    )


async def fail_tool_call(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记失败工具调用，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                UPDATE ai_tool_call SET status='FAILED',error_code='TOOL_FAILED',
                finished_at=UTC_TIMESTAMP(6) WHERE id=:id AND status='STARTED'
            """),
        parameters,
    )


async def insert_generated_fact(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记生成事实，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                INSERT INTO ai_diagnostic_fact
                (id,conversation_id,state_version,fact_kind,statement,source_type,source_generation_id,source_locator)
                VALUES
                (:id,:conversation,:version,:kind,:statement,'ASSISTANT_PROPOSAL',:run,:locator)
            """),
        parameters,
    )


async def insert_citation(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记引用，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                INSERT INTO ai_citation
                (id,generation_id,source_usage_id,citation_number,title_snapshot,locator_snapshot,evidence_excerpt)
                VALUES (:id,:run,:source,:number,:title,:locator,:excerpt)
            """),
        parameters,
    )


async def call_usage(db: AsyncConnection, parameters: Mapping[str, object]) -> Sequence[Row[tuple]]:
    """读取调用使用记录，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text("""
                SELECT input_tokens,output_tokens FROM ai_model_call WHERE generation_id=:id
            """),
            parameters,
        )
    ).all()


async def complete_generation(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """提交成功运行，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                UPDATE ai_generation SET
                status='SUCCEEDED',answer_type=:type,answer_text=:answer,
                structured_result=:structured,output_state_version=:version,answer_model=:model,input_tokens=:input,
                output_tokens=:output,finished_at=UTC_TIMESTAMP(6),claim_token=NULL,claim_until=NULL,
                latency_ms=TIMESTAMPDIFF(MICROSECOND,created_at,UTC_TIMESTAMP(6)) DIV
                1000,updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """),
        parameters,
    )


async def advance_state(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """推进状态，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text("""
                UPDATE ai_conversation SET state_version=:version,updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id
            """),
        parameters,
    )
