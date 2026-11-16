"""本人会话、事实与生成受理的具名查询及写入；事务与授权由调用用例持有。作者：yangaobo。"""

from collections.abc import Mapping, Sequence

from sqlalchemy import text
from sqlalchemy.engine import Row, RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection


async def insert_conversation(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记会话，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                INSERT INTO ai_conversation
                (id,owner_id,customer_id,mode,ticket_id,service_item_id,device_id,product_model_id,support_context,title)
                VALUES
                (:id,:owner,:customer,:mode,:ticket,:service,:device,:model,:context,:title)
            """
        ),
        parameters,
    )


async def count_conversations(db: AsyncConnection, parameters: Mapping[str, object]) -> int:
    """统计会话，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text(
                """
                SELECT COUNT(*) FROM ai_conversation WHERE owner_id=:owner AND deleted_at IS
                NULL AND (:mode IS NULL OR mode=:mode) AND (:ticket IS NULL OR
                ticket_id=:ticket)
            """
            ),
            parameters,
        )
    ).scalar_one()


async def page_conversation_ids(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Sequence[str]:
    """分页读取会话标识，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT id FROM ai_conversation WHERE owner_id=:owner AND deleted_at IS NULL AND
                (:mode IS NULL OR mode=:mode) AND (:ticket IS NULL OR ticket_id=:ticket) ORDER
                BY updated_at DESC,id LIMIT :limit OFFSET :offset
            """
                ),
                parameters,
            )
        )
        .scalars()
        .all()
    )


async def lock_active_generation_ids(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Sequence[str]:
    """加锁读取活动运行标识，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT id FROM ai_generation WHERE conversation_id=:id AND status IN
                ('QUEUED','RUNNING') FOR UPDATE
            """
                ),
                parameters,
            )
        )
        .scalars()
        .all()
    )


async def mark_conversation_deleted(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """标记会话删除，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_conversation SET deleted_at=UTC_TIMESTAMP(6),
                row_version=row_version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """
        ),
        parameters,
    )


async def rename_conversation(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """修改名称会话，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_conversation SET title=:title,row_version=row_version+1,
                updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """
        ),
        parameters,
    )


async def count_facts(db: AsyncConnection, parameters: Mapping[str, object]) -> int:
    """统计事实，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text("SELECT COUNT(*) FROM ai_diagnostic_fact WHERE conversation_id=:id"), parameters
        )
    ).scalar_one()


async def page_facts(db: AsyncConnection, parameters: Mapping[str, object]) -> Sequence[RowMapping]:
    """分页读取事实，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT f.id,f.state_version,f.fact_kind,f.operation,f.statement,f.source_type,
                f.supersedes_fact_id,f.source_generation_id,f.source_locator,f.created_at,
                (f.operation='ASSERT' AND NOT EXISTS(SELECT 1 FROM ai_diagnostic_fact later
                WHERE later.conversation_id=f.conversation_id AND
                later.supersedes_fact_id=f.id)) AS active FROM ai_diagnostic_fact f WHERE
                f.conversation_id=:id ORDER BY f.state_version,f.id LIMIT :limit OFFSET :offset
            """
                ),
                parameters,
            )
        )
        .mappings()
        .all()
    )


async def find_fact_request(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Row[tuple] | None:
    """查询事实请求回执，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text(
                """
                SELECT action_request_hash FROM ai_diagnostic_fact WHERE conversation_id=:id AND
                client_action_id=:key
            """
            ),
            parameters,
        )
    ).first()


async def lock_current_assertion(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Row[tuple] | None:
    """加锁读取当前有效断言，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text(
                """
                SELECT id FROM ai_diagnostic_fact f WHERE f.id=:fact AND f.conversation_id=:id
                AND f.operation='ASSERT' AND NOT EXISTS (SELECT 1 FROM ai_diagnostic_fact s
                WHERE s.supersedes_fact_id=f.id)
            """
            ),
            parameters,
        )
    ).first()


async def insert_user_fact(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记用户事实，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                INSERT INTO ai_diagnostic_fact
                (id,conversation_id,state_version,fact_kind,operation,statement,source_type,reported_by,
                source_locator,supersedes_fact_id,client_action_id,action_request_hash) VALUES
                (:fact,:id,:version,:kind,:operation,:statement,:source,:actor,:locator,:predecessor,:key,:hash)
            """
        ),
        parameters,
    )


async def update_environment(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """更新环境，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_conversation SET
                support_context=:context,state_version=state_version+1,
                updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """
        ),
        parameters,
    )


async def queued_generation_count(db: AsyncConnection) -> int:
    """统计排队运行数量，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(text("SELECT COUNT(*) FROM ai_generation WHERE status='QUEUED'"))
    ).scalar_one()


async def next_round_number(db: AsyncConnection, parameters: Mapping[str, object]) -> int:
    """计算下一轮次序号，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text(
                """
                SELECT COALESCE(MAX(round_number),0)+1 FROM ai_generation WHERE
                conversation_id=:id
            """
            ),
            parameters,
        )
    ).scalar_one()


async def insert_generation(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记运行，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                INSERT INTO ai_generation
                (id,conversation_id,owner_id,round_number,input_state_version,ticket_version,question_text,
                client_request_id,request_hash,session_ref,auth_version,pipeline_config,context_snapshot,trace_context)
                VALUES
                (:id,:conversation,:owner,:round,:version,:ticket_version,:question,:key,:hash,:session,:auth,:pipeline,:context,:trace)
            """
        ),
        parameters,
    )


async def insert_question_fact(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """登记问题事实，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                INSERT INTO ai_diagnostic_fact
                (id,conversation_id,state_version,fact_kind,statement,source_type,source_generation_id,reported_by,source_locator)
                VALUES
                (:id,:conversation,:version,'OBSERVATION',:question,:source,:run,:actor,:locator)
            """
        ),
        parameters,
    )


async def advance_environment(db: AsyncConnection, parameters: Mapping[str, object]) -> None:
    """推进环境，沿调用方连接执行，不开启或提交事务。"""
    await db.execute(
        text(
            """
                UPDATE ai_conversation SET
                state_version=state_version+1,support_context=:context,
                updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """
        ),
        parameters,
    )


async def generation_citations(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Sequence[RowMapping]:
    """读取运行引用，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT c.citation_number,c.source_usage_id,c.title_snapshot,
                c.locator_snapshot,c.evidence_excerpt,u.source_version FROM ai_citation c JOIN
                ai_source_usage u ON u.id=c.source_usage_id WHERE c.generation_id=:id ORDER BY
                c.citation_number
            """
                ),
                parameters,
            )
        )
        .mappings()
        .all()
    )


async def count_turns(db: AsyncConnection, parameters: Mapping[str, object]) -> int:
    """统计历史轮次，沿调用方连接执行，不开启或提交事务。"""
    return (
        await db.execute(
            text("SELECT COUNT(*) FROM ai_generation WHERE conversation_id=:id"), parameters
        )
    ).scalar_one()


async def page_generation_ids(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Sequence[str]:
    """分页读取运行标识，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT id FROM ai_generation WHERE conversation_id=:id ORDER BY round_number
                LIMIT :limit OFFSET :offset
            """
                ),
                parameters,
            )
        )
        .scalars()
        .all()
    )


async def model_calls(
    db: AsyncConnection, parameters: Mapping[str, object]
) -> Sequence[RowMapping]:
    """读取模型调用，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT call_number,purpose,status,requested_model,response_model,
                input_tokens,output_tokens,estimated_cost FROM ai_model_call WHERE
                generation_id=:id ORDER BY call_number
            """
                ),
                parameters,
            )
        )
        .mappings()
        .all()
    )


async def tool_calls(db: AsyncConnection, parameters: Mapping[str, object]) -> Sequence[RowMapping]:
    """读取工具调用，沿调用方连接执行，不开启或提交事务。"""
    return (
        (
            await db.execute(
                text(
                    """
                SELECT tool_number,tool_name,status,result_summary,result_refs FROM ai_tool_call
                WHERE generation_id=:id ORDER BY tool_number
            """
                ),
                parameters,
            )
        )
        .mappings()
        .all()
    )
