"""诊断任务聚合的明确查询和持久动作；事务、授权及工作流由用例负责。作者：yangaobo。"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from typing import Literal

from sqlalchemy import text
from sqlalchemy.engine import RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection

from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories.values import json_value
from servicedesk_ai.schemas.tasks import (
    AnswerSchema,
    CheckDependencies,
    CheckStatus,
    ContextChange,
    HypothesisState,
    NormalizedAnswer,
    TaskBudget,
    TaskEnvironment,
    TaskEventDetails,
    TaskStatus,
)


async def latest_check(db: AsyncConnection, task_id: str, check_key: str) -> RowMapping | None:
    """按任务内稳定检查键查询最新实例，复查身份不由模型猜测。"""
    return (
        (
            await db.execute(
                text("""
            SELECT id,status,attempt_number FROM ai_diagnostic_check
            WHERE task_id=:task AND check_key=:key ORDER BY attempt_number DESC LIMIT 1
        """),
                {"task": task_id, "key": check_key},
            )
        )
        .mappings()
        .first()
    )


@dataclass(frozen=True)
class DiagnosisTask:
    """任务是环境、检查和事实版本的唯一权威，不将生成轮次当任务进度。"""

    id: str
    conversation_id: str
    service_item_id: str | None
    device_id: str | None
    product_model_id: str | None
    create_action_id: str
    create_request_hash: str
    goal: str
    status: TaskStatus
    task_state_version: int
    diagnosis_context_version: int
    support_context: TaskEnvironment
    hypothesis_state: HypothesisState
    ticket_event_sequence: int
    procedure_document_id: str | None
    procedure_sha256: str | None
    budget_policy: TaskBudget
    blocking_draft_root_id: str | None
    blocked_reason: str | None
    outcome_source: Literal["USER_REPORT", "HUMAN_RECORD", "SYSTEM_RULE"] | None
    outcome_note: str | None
    handover_ticket_id: str | None
    closed_at: datetime | None
    created_at: datetime
    updated_at: datetime
    row_version: int


@dataclass(frozen=True)
class DiagnosticCheck:
    """每个检查身份只接受一次反馈；复查建立新的实例。"""

    id: str
    task_id: str
    proposed_run_id: str
    check_key: str
    check_kind: Literal["CLARIFICATION", "CHECK"]
    attempt_number: int
    procedure_document_id: str | None
    procedure_check_key: str | None
    instruction: str
    answer_schema: AnswerSchema
    status: CheckStatus
    executor_type: Literal["CUSTOMER", "TECHNICIAN", "TOOL"]
    requested_actor_id: str | None
    context_version: int
    dependency_fields: CheckDependencies
    answer_message_id: str | None
    normalized_answer: NormalizedAnswer | None
    answered_by: str | None
    answered_at: datetime | None
    result_fact_id: str | None
    previous_check_id: str | None
    expires_at: datetime | None
    created_at: datetime
    updated_at: datetime
    row_version: int


async def context_facts(
    db: AsyncConnection,
    task_id: str,
    state_version: int,
    before_id: str | None,
    query: str | None,
    limit: int,
) -> list[RowMapping]:
    """按受理版本读取仍有效事实；游标及字面子串均限制在同一任务内。"""
    pattern = (
        None
        if query is None
        else "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
    )
    rows = await db.execute(
        text("""
        SELECT f.id,f.state_version,f.statement,f.fact_kind,f.source_type,
            f.environment_snapshot,f.created_at,c.run_id AS source_run_id
        FROM ai_diagnostic_fact f LEFT JOIN ai_tool_call c ON c.id=f.source_tool_call_id
        WHERE f.task_id=:task AND f.state_version<=:version AND f.operation<>'RETRACT'
          AND NOT EXISTS(SELECT 1 FROM ai_diagnostic_fact s
              WHERE s.supersedes_fact_id=f.id AND s.state_version<=:version)
          AND (:before IS NULL OR f.id<:before)
          AND (:pattern IS NULL OR f.statement LIKE :pattern ESCAPE '!')
        ORDER BY f.id DESC LIMIT :limit
    """),
        {
            "task": task_id,
            "version": state_version,
            "before": before_id,
            "pattern": pattern,
            "limit": limit,
        },
    )
    return list(rows.mappings())


async def context_checks(
    db: AsyncConnection,
    task_id: str,
    before_id: str | None,
    query: str | None,
    limit: int,
) -> list[RowMapping]:
    """当前任务检查的稳定ID游标；状态变更由调用方再次核对任务版本。"""
    pattern = (
        None
        if query is None
        else "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
    )
    rows = await db.execute(
        text("""
        SELECT c.id,c.check_key,c.check_kind,c.instruction,c.status,c.normalized_answer,
            c.context_version,c.answered_at,
            c.row_version,c.proposed_run_id,c.answer_message_id,c.result_fact_id,
            m.content_sha256 AS answer_sha256
        FROM ai_diagnostic_check c LEFT JOIN ai_message m ON m.id=c.answer_message_id
          AND m.task_id=c.task_id AND m.role='USER'
        WHERE c.task_id=:task AND (:before IS NULL OR c.id<:before)
          AND (:pattern IS NULL OR c.instruction LIKE :pattern ESCAPE '!'
            OR c.check_key LIKE :pattern ESCAPE '!')
        ORDER BY c.id DESC LIMIT :limit
    """),
        {"task": task_id, "before": before_id, "pattern": pattern, "limit": limit},
    )
    return list(rows.mappings())


def decode_task(row: RowMapping) -> DiagnosisTask:
    """数据库JSON在读取边界校验，未知字段或错误版本不能进入诊断状态。"""
    values = {key: row[key] for key in DiagnosisTask.__dataclass_fields__}
    for name, kind in (
        ("support_context", TaskEnvironment),
        ("hypothesis_state", HypothesisState),
        ("budget_policy", TaskBudget),
    ):
        raw = values[name]
        values[name] = (
            kind.model_validate_json(raw) if isinstance(raw, str) else kind.model_validate(raw)
        )
    return DiagnosisTask(**values)


def decode_check(row: RowMapping) -> DiagnosticCheck:
    """持久答案保留原语义，数据库空值不能变成默认回答。"""
    values = {key: row[key] for key in DiagnosticCheck.__dataclass_fields__}
    for name in ("answer_schema", "normalized_answer", "dependency_fields"):
        if isinstance(values[name], str):
            values[name] = json.loads(values[name])
    values["answer_schema"] = AnswerSchema.model_validate(values["answer_schema"])
    values["dependency_fields"] = CheckDependencies.model_validate(values["dependency_fields"])
    if values["normalized_answer"] is not None:
        values["normalized_answer"] = NormalizedAnswer.model_validate(values["normalized_answer"])
    return DiagnosticCheck(**values)


async def task(db: AsyncConnection, identifier: str, *, lock: bool = False) -> DiagnosisTask:
    """只读取明确列；写用例必须先锁所属conversation再请求任务锁。"""
    columns = ",".join(DiagnosisTask.__dataclass_fields__)
    row = (
        (
            await db.execute(
                text(
                    f"SELECT {columns} FROM ai_diagnosis_task WHERE id=:id"
                    + (" FOR UPDATE" if lock else "")
                ),
                {"id": identifier},
            )
        )
        .mappings()
        .first()
    )
    if row is None:
        raise BusinessError(CommonErrorCode.NOT_FOUND)
    return decode_task(row)


async def check(
    db: AsyncConnection, task_id: str, identifier: str, *, lock: bool = False
) -> DiagnosticCheck:
    """检查必须属于当前任务，不能只验证标识存在。"""
    columns = ",".join(DiagnosticCheck.__dataclass_fields__)
    row = (
        (
            await db.execute(
                text(
                    f"SELECT {columns} FROM ai_diagnostic_check WHERE task_id=:task AND id=:id"
                    + (" FOR UPDATE" if lock else "")
                ),
                {"task": task_id, "id": identifier},
            )
        )
        .mappings()
        .first()
    )
    if row is None:
        raise BusinessError(CommonErrorCode.NOT_FOUND)
    return decode_check(row)


async def action_receipt(
    db: AsyncConnection, task_id: str, actor_id: str, key: str, digest: str
) -> TaskEventDetails | None:
    """原动作回执先于当前版本及终态判定；同键异义明确冲突。"""
    row = (
        (
            await db.execute(
                text("""
        SELECT request_hash,details FROM ai_task_event
        WHERE task_id=:task AND actor_id=:actor AND client_action_id=:key
    """),
                {"task": task_id, "actor": actor_id, "key": key},
            )
        )
        .mappings()
        .first()
    )
    if row is None:
        return None
    if row["request_hash"] != digest:
        raise BusinessError(CommonErrorCode.CONFLICT)
    return TaskEventDetails.model_validate_json(row["details"])


async def answer_receipt(db: AsyncConnection, task_id: str, message_id: str) -> TaskEventDetails:
    """相同规范答案跨动作键重放时返回首次提交事件，不修改原答案。"""
    raw = (
        await db.execute(
            text("""
        SELECT details FROM ai_task_event WHERE task_id=:task AND event_type='CHECK_ANSWERED'
        AND JSON_UNQUOTE(details->'$.receipt.input_message_id')=:message
    """),
            {"task": task_id, "message": message_id},
        )
    ).scalar_one()
    return TaskEventDetails.model_validate_json(raw)


async def assert_idle(db: AsyncConnection, conversation_id: str) -> None:
    """调用方持conversation锁，所有受理者同锁，避免更正与新Run穿插。"""
    active = (
        await db.execute(
            text("""
        SELECT id FROM ai_run WHERE active_conversation_id=:conversation LIMIT 1
    """),
            {"conversation": conversation_id},
        )
    ).first()
    if active is not None:
        raise BusinessError(CommonErrorCode.CONFLICT)


async def completed_run_event(
    db: AsyncConnection, task_id: str, state_version: int
) -> TaskEventDetails | None:
    """读取运行完成时提交的任务事件，后续反馈或更正在独立版本。"""
    value = (
        await db.execute(
            text(
                "SELECT details FROM ai_task_event WHERE task_id=:task "
                "AND state_version=:version AND event_type='RUN_COMPLETED'"
            ),
            {"task": task_id, "version": state_version},
        )
    ).scalar_one_or_none()
    return TaskEventDetails.model_validate_json(value) if value is not None else None


async def decision_time(db: AsyncConnection) -> datetime:
    """有效期与数据库租约共用UTC时钟，不能按Web进程本地时间决定。"""
    return (await db.execute(text("SELECT UTC_TIMESTAMP(6)"))).scalar_one()


async def append_event(
    db: AsyncConnection,
    task_id: str,
    actor_id: str,
    key: str,
    digest: str,
    event_type: str,
    details: TaskEventDetails,
) -> None:
    """事件不可变，原回执与投影在同一事务提交。"""
    payload = details.model_dump_json()
    if len(payload.encode("utf-8")) > 96 * 1024:
        raise BusinessError(CommonErrorCode.PAYLOAD_TOO_LARGE)
    await db.execute(
        text("""
        INSERT INTO ai_task_event(id,task_id,state_version,actor_type,actor_id,client_action_id,
                                  request_hash,event_type,details)
        VALUES (:id,:task,:version,'USER',:actor,:key,:hash,:type,:details)
    """),
        {
            "id": details.receipt.event_id,
            "task": task_id,
            "version": int(details.receipt.task_state_version),
            "actor": actor_id,
            "key": key,
            "hash": digest,
            "type": event_type,
            "details": payload,
        },
    )


async def advance(
    db: AsyncConnection,
    previous: DiagnosisTask,
    *,
    status: TaskStatus,
    hypotheses: HypothesisState,
    context: TaskEnvironment,
    context_version: int,
    closed_at: datetime | None = None,
    outcome_source: str | None = None,
    outcome_note: str | None = None,
    blocking_draft_root_id: str | None = None,
    handover_ticket_id: str | None = None,
) -> None:
    """人工动作统一推进两种任务版本，非审批/阻塞状态清空专属字段。"""
    result = await db.execute(
        text("""
        UPDATE ai_diagnosis_task SET status=:status,task_state_version=task_state_version+1,
        row_version=row_version+1,updated_at=UTC_TIMESTAMP(6),hypothesis_state=:hypotheses,
        support_context=:context,diagnosis_context_version=:context_version,
        blocking_draft_root_id=CASE WHEN :status='WAITING_APPROVAL'
            THEN :blocking ELSE NULL END,
        handover_ticket_id=COALESCE(:handover,handover_ticket_id),
        blocked_reason=CASE WHEN :status='BLOCKED_EXTERNAL' THEN blocked_reason ELSE NULL END,
        closed_at=:closed,outcome_source=:source,outcome_note=:note
        WHERE id=:id AND row_version=:version AND task_state_version=:state
    """),
        {
            "id": previous.id,
            "version": previous.row_version,
            "state": previous.task_state_version,
            "status": status,
            "hypotheses": hypotheses.model_dump_json(),
            "context": context.model_dump_json(),
            "context_version": context_version,
            "closed": closed_at,
            "source": outcome_source,
            "note": outcome_note,
            "blocking": blocking_draft_root_id or previous.blocking_draft_root_id,
            "handover": handover_ticket_id,
        },
    )
    if result.rowcount != 1:
        raise BusinessError(CommonErrorCode.CONFLICT)


async def append_message(
    db: AsyncConnection,
    *,
    identifier: str,
    task: DiagnosisTask,
    content: str,
    content_sha256: str,
    key: str,
    request_hash: str,
) -> None:
    """锁定会话后分配序号，输入与反馈原子落库，不隐式创建Run。"""
    if not content.strip() or len(content) > 12000 or len(content.encode("utf-8")) > 48 * 1024:
        raise BusinessError(CommonErrorCode.INVALID_REQUEST)
    result = await db.execute(
        text("""
        UPDATE ai_conversation SET message_sequence=message_sequence+1,updated_at=UTC_TIMESTAMP(6)
        WHERE id=:conversation AND deleted_at IS NULL
    """),
        {"conversation": task.conversation_id},
    )
    if result.rowcount != 1:
        raise BusinessError(CommonErrorCode.CONFLICT)
    await db.execute(
        text("""
        INSERT INTO ai_message(id,conversation_id,task_id,sequence_number,role,content,
                               content_sha256,client_request_id,request_hash)
        SELECT :id,id,:task,message_sequence,'USER',:content,:sha,:key,:hash
        FROM ai_conversation WHERE id=:conversation AND deleted_at IS NULL
    """),
        {
            "id": identifier,
            "task": task.id,
            "conversation": task.conversation_id,
            "content": content,
            "sha": content_sha256,
            "key": key,
            "hash": request_hash,
        },
    )


async def append_fact(
    db: AsyncConnection,
    *,
    identifier: str,
    task: DiagnosisTask,
    kind: str,
    operation: str,
    statement: str,
    source_type: str,
    actor_id: str,
    message_id: str,
    predecessor: str | None = None,
    reported_conditions: tuple[str, ...] = (),
) -> None:
    """人工反馈始终带同任务原始消息；前驱有效性由用例持任务锁验证。"""
    await db.execute(
        text("""
        INSERT INTO ai_diagnostic_fact(id,task_id,state_version,fact_kind,operation,statement,
        source_type,message_id,reported_by,source_locator,environment_snapshot,supersedes_fact_id)
        VALUES (:id,:task,:version,:kind,:operation,:statement,:source,:message,
                :actor,:locator,:environment,:predecessor)
    """),
        {
            "id": identifier,
            "task": task.id,
            "version": task.task_state_version + 1,
            "kind": kind,
            "operation": operation,
            "statement": statement,
            "source": source_type,
            "message": message_id,
            "actor": actor_id,
            "locator": json_value(
                {
                    "schema_version": 1,
                    "message_id": message_id,
                    "reported_conditions": list(reported_conditions),
                }
            ),
            "environment": json_value(
                {
                    "schema_version": 1,
                    "diagnosis_context_version": task.diagnosis_context_version,
                    "environment": task.support_context.model_dump(mode="json"),
                }
            ),
            "predecessor": predecessor,
        },
    )


async def reported_conditions(db: AsyncConnection, task_id: str) -> set[str]:
    """所有未更正的人工条件均参与宿主停止，不被上下文截取或环境换版隐式清除。"""
    rows = await db.execute(
        text("""
        SELECT DISTINCT condition_value.code
        FROM ai_diagnostic_fact f
        JOIN JSON_TABLE(f.source_locator, '$.reported_conditions[*]'
            COLUMNS(code VARCHAR(64) PATH '$')) AS condition_value
        WHERE f.task_id=:task AND f.operation<>'RETRACT'
          AND f.source_type IN ('USER_REPORT','HUMAN_RECORD')
          AND NOT EXISTS(SELECT 1 FROM ai_diagnostic_fact s WHERE s.supersedes_fact_id=f.id)
    """),
        {"task": task_id},
    )
    return set(rows.scalars())


async def answer_check(
    db: AsyncConnection,
    value: DiagnosticCheck,
    answer: NormalizedAnswer,
    message_id: str,
    fact_id: str,
    actor_id: str,
    now: datetime,
) -> None:
    """同一实例只从OPEN进入ANSWERED；ANSWERED绝不表示已执行或已恢复。"""
    result = await db.execute(
        text("""
        UPDATE ai_diagnostic_check SET status='ANSWERED',answer_message_id=:message,
        normalized_answer=:answer,answered_by=:actor,answered_at=:now,result_fact_id=:fact,
        row_version=row_version+1,updated_at=:now
        WHERE id=:id AND task_id=:task AND status='OPEN' AND row_version=:version
        AND requested_actor_id=:actor AND expires_at>:now
    """),
        {
            "id": value.id,
            "task": value.task_id,
            "version": value.row_version,
            "message": message_id,
            "answer": answer.model_dump_json(),
            "actor": actor_id,
            "now": now,
            "fact": fact_id,
        },
    )
    if result.rowcount != 1:
        raise BusinessError(CommonErrorCode.CONFLICT)


async def active_fact(db: AsyncConnection, task_id: str, identifier: str) -> RowMapping:
    """更正只允许同任务无后继且未撤回的事实，数据库唯一键防止分叉。"""
    row = (
        (
            await db.execute(
                text("""
        SELECT f.id,f.fact_kind,f.source_type FROM ai_diagnostic_fact f
        WHERE f.id=:id AND f.task_id=:task AND f.operation<>'RETRACT'
        AND NOT EXISTS(SELECT 1 FROM ai_diagnostic_fact s WHERE s.supersedes_fact_id=f.id)
    """),
                {"id": identifier, "task": task_id},
            )
        )
        .mappings()
        .first()
    )
    if row is None:
        raise BusinessError(CommonErrorCode.CONFLICT)
    return row


async def open_check_count(db: AsyncConnection, task_id: str) -> int:
    """反馈后只按真实剩余OPEN项决定是否仍等待，不推断故障结果。"""
    return (
        await db.execute(
            text("SELECT COUNT(*) FROM ai_diagnostic_check WHERE task_id=:task AND status='OPEN'"),
            {"task": task_id},
        )
    ).scalar_one()


async def created_task(db: AsyncConnection, conversation_id: str, key: str) -> DiagnosisTask | None:
    """会话范围创建键在版本、活动任务及当前服务资格之前核对。"""
    identifier = (
        await db.execute(
            text("""
        SELECT id FROM ai_diagnosis_task
        WHERE conversation_id=:conversation AND create_action_id=:key
    """),
            {"conversation": conversation_id, "key": key},
        )
    ).scalar_one_or_none()
    return await task(db, identifier) if identifier else None


async def insert_task(db: AsyncConnection, value: DiagnosisTask) -> None:
    """只保存已授权创建快照；唯一活动会话约束仍是最终并发边界。"""
    active = (
        await db.execute(
            text("SELECT id FROM ai_diagnosis_task WHERE active_conversation_id=:conversation"),
            {"conversation": value.conversation_id},
        )
    ).first()
    if active:
        raise BusinessError(CommonErrorCode.CONFLICT)
    await db.execute(
        text("""
        INSERT INTO ai_diagnosis_task(id,conversation_id,service_item_id,device_id,product_model_id,
            create_action_id,create_request_hash,goal,support_context,hypothesis_state,
            diagnosis_context_version,ticket_event_sequence,budget_policy,created_at,updated_at)
        VALUES (:id,:conversation,:service,:device,:model,:key,:hash,:goal,:context,:hypotheses,
                :context_version,:sequence,:budget,:now,:now)
    """),
        {
            "id": value.id,
            "conversation": value.conversation_id,
            "service": value.service_item_id,
            "device": value.device_id,
            "model": value.product_model_id,
            "key": value.create_action_id,
            "hash": value.create_request_hash,
            "goal": value.goal,
            "context": value.support_context.model_dump_json(),
            "hypotheses": value.hypothesis_state.model_dump_json(),
            "context_version": value.diagnosis_context_version,
            "sequence": value.ticket_event_sequence,
            "budget": value.budget_policy.model_dump_json(),
            "now": value.created_at,
        },
    )


async def withdraw_checks(db: AsyncConnection, task_id: str) -> list[str]:
    """关闭任务撤回剩余待答项，已回答结果保留；有界异常不静默截断。"""
    identifiers = list(
        (
            await db.execute(
                text("""
        SELECT id FROM ai_diagnostic_check WHERE task_id=:task AND status='OPEN'
        ORDER BY id LIMIT 2049
    """),
                {"task": task_id},
            )
        ).scalars()
    )
    if len(identifiers) > 2048:
        raise BusinessError(CommonErrorCode.CONFLICT)
    await db.execute(
        text("""
        UPDATE ai_diagnostic_check SET status='WITHDRAWN',row_version=row_version+1,
        updated_at=UTC_TIMESTAMP(6) WHERE task_id=:task AND status='OPEN'
    """),
        {"task": task_id},
    )
    return identifiers


async def withdraw_drafts(db: AsyncConnection, task_id: str) -> None:
    """任务结束撤回未采用提议；正式结果仍由ticket回执决定。"""
    await db.execute(
        text("""
        UPDATE ai_action_draft SET status='WITHDRAWN' WHERE task_id=:task AND status='PENDING'
    """),
        {"task": task_id},
    )


async def invalidate_checks(
    db: AsyncConnection, value: DiagnosisTask, change: ContextChange
) -> list[str]:
    """只失效依赖变化字段的实例；原答复保留，不能通过改字段重写历史。"""
    rows = (
        (
            await db.execute(
                text("""
        SELECT id,dependency_fields FROM ai_diagnostic_check WHERE task_id=:task
        AND status IN ('OPEN','ANSWERED','OBSERVED_DONE') ORDER BY id LIMIT 2049
    """),
                {"task": value.id},
            )
        )
        .mappings()
        .all()
    )
    if len(rows) > 2048:
        raise BusinessError(CommonErrorCode.CONFLICT)
    changed = []
    for row in rows:
        fields = CheckDependencies.model_validate_json(row["dependency_fields"]).items
        if not change.scope_changed and not set(fields).intersection(change.changed_fields):
            continue
        await db.execute(
            text("""
            UPDATE ai_diagnostic_check SET status='NEEDS_RECHECK',row_version=row_version+1,
            updated_at=UTC_TIMESTAMP(6) WHERE id=:id AND task_id=:task
        """),
            {"id": row["id"], "task": value.id},
        )
        changed.append(row["id"])
    return changed


async def check_context_valid(
    db: AsyncConnection, value: DiagnosisTask, check: DiagnosticCheck
) -> bool:
    """跨版本检查沿环境事件逐段证明依赖未变；缺失历史或过多变化均拒绝复用。"""
    if check.context_version == value.diagnosis_context_version:
        return True
    if check.context_version > value.diagnosis_context_version:
        return False
    rows = (
        (
            await db.execute(
                text("""
        SELECT details FROM ai_task_event WHERE task_id=:task AND event_type='CONTEXT_SYNCED'
        AND CAST(details->'$.context_change.current_version' AS UNSIGNED)>:version
        ORDER BY state_version LIMIT 65
    """),
                {"task": value.id, "version": check.context_version},
            )
        )
        .scalars()
        .all()
    )
    if len(rows) > 64:
        return False
    current = check.context_version
    dependencies = check.dependency_fields.items
    for raw in rows:
        change = TaskEventDetails.model_validate_json(raw).context_change
        if (
            change is None
            or change.previous_version != current
            or change.current_version <= current
            or change.scope_changed
            or set(dependencies).intersection(change.changed_fields)
        ):
            return False
        current = change.current_version
    return current == value.diagnosis_context_version


async def synchronize_scope(
    db: AsyncConnection,
    value: DiagnosisTask,
    service_item_id: str | None,
    device_id: str | None,
    product_model_id: str | None,
    event_sequence: int,
) -> None:
    """同步可信范围或首次分类，范围变化撤销原规程绑定，历史检查和来源保留。"""
    result = await db.execute(
        text("""
        UPDATE ai_diagnosis_task SET service_item_id=:service,device_id=:device,
            product_model_id=:model,ticket_event_sequence=:sequence,
            procedure_document_id=IF(:scope_changed,NULL,procedure_document_id),
            procedure_sha256=IF(:scope_changed,NULL,procedure_sha256)
        WHERE id=:id AND row_version=:version
    """),
        {
            "id": value.id,
            "version": value.row_version,
            "service": service_item_id,
            "device": device_id,
            "model": product_model_id,
            "sequence": event_sequence,
            "scope_changed": (value.service_item_id, value.device_id, value.product_model_id)
            != (service_item_id, device_id, product_model_id),
        },
    )
    if result.rowcount != 1:
        raise BusinessError(CommonErrorCode.CONFLICT)


async def expiry_candidates(db: AsyncConnection) -> list[tuple[str, str]]:
    """按检查到期索引扫描至多20个任务，工作流须锁后重新核验。"""
    rows = (
        await db.execute(
            text("""
        SELECT DISTINCT t.id,t.conversation_id FROM ai_diagnostic_check c
        JOIN ai_diagnosis_task t ON t.id=c.task_id
        WHERE c.status='OPEN' AND c.expires_at<=UTC_TIMESTAMP(6)
        ORDER BY t.id LIMIT 20
    """)
        )
    ).all()
    return [(row[0], row[1]) for row in rows]


async def expire_due_checks(db: AsyncConnection, task_id: str, now: datetime) -> list[str]:
    """调用方已锁会话及任务；每批最多100项，不写人工答案或工具观测。"""
    ids = list(
        (
            await db.execute(
                text("""
        SELECT id FROM ai_diagnostic_check WHERE task_id=:task AND status='OPEN'
          AND expires_at<=:now ORDER BY id LIMIT 100 FOR UPDATE
    """),
                {"task": task_id, "now": now},
            )
        ).scalars()
    )
    for identifier in ids:
        await db.execute(
            text("""
            UPDATE ai_diagnostic_check SET status='EXPIRED',row_version=row_version+1,
                updated_at=:now WHERE id=:id AND status='OPEN'
        """),
            {"id": identifier, "now": now},
        )
    return ids


async def append_expiry_event(db: AsyncConnection, details: TaskEventDetails) -> None:
    """系统事件没有用户动作键；与检查状态及任务版本同事务提交。"""
    await append_system_event(db, "CHECK_EXPIRED", details)


async def append_system_event(
    db: AsyncConnection, event_type: str, details: TaskEventDetails
) -> None:
    """系统收敛不伪造用户动作键，事件和任务状态共用调用方事务。"""
    await db.execute(
        text("""
        INSERT INTO ai_task_event(id,task_id,state_version,actor_type,event_type,details)
        VALUES (:id,:task,:version,'SYSTEM',:type,:details)
    """),
        {
            "id": details.receipt.event_id,
            "task": details.receipt.task_id,
            "version": int(details.receipt.task_state_version),
            "details": details.model_dump_json(),
            "type": event_type,
        },
    )
