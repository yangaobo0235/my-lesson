"""Run/消息/调用公开读取，全部来源授权与供应商真实计量共用持久账目。作者：yangaobo。"""

import json

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine

from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.evidence import EvidenceErrorCode
from servicedesk_ai.repositories import runs, tasks
from servicedesk_ai.schemas.diagnosis_views import (
    CallsView,
    CitationView,
    ModelCallView,
    PageView,
    ToolCallView,
)
from servicedesk_ai.schemas.procedures import ProcedureSnapshot
from servicedesk_ai.schemas.run_views import MessagePage, MessageView, RunTaskUpdate, RunView
from servicedesk_ai.schemas.runs import RunResult
from servicedesk_ai.services.conversations import ConversationService, instant
from servicedesk_ai.services.evidence import Evidence, EvidenceService
from servicedesk_ai.services.runs import RunService


def citation_excerpt(item: Evidence) -> str:
    """规程引用展示原指令和全部安全约束；持久化证据仍保留完整审核快照。"""
    if item.source_type != "PROCEDURE_CHECK":
        return item.excerpt
    snapshot = ProcedureSnapshot.model_validate(item.source_version["procedure"])
    check = snapshot.check
    conditions = {
        "SAFETY_HAZARD_REPORTED": "报告安全危险",
        "DATA_LOSS_RISK_REPORTED": "报告数据丢失风险",
        "ADMIN_PRIVILEGE_REQUIRED": "需要管理员权限",
        "OUT_OF_SERVICE_SCOPE": "超出服务范围",
        "NO_PROGRESS_LIMIT": "达到无进展上限",
        "EVIDENCE_CONFLICT": "证据相互冲突",
    }
    fields = {
        "os_name": "操作系统",
        "os_version": "系统版本",
        "software_name": "软件名称",
        "software_version": "软件版本",
        "connection_type": "连接方式",
        "affected_scope": "影响范围",
        "error_code": "错误码",
    }
    required = list(dict.fromkeys(snapshot.required_fields + check.required_fields))
    stops = list(dict.fromkeys(snapshot.stop_conditions + check.stop_conditions))
    lines = [
        "适用范围：" + snapshot.applicability,
        "检查指令：" + check.instruction,
        "适用角色："
        + "、".join("客户" if role == "CUSTOMER" else "技术人员" for role in check.roles),
        "风险等级：" + ("需人工接手" if check.risk_level == "HANDOVER_REQUIRED" else "低风险"),
    ]
    if required:
        lines.append("先确认环境：" + "、".join(fields[name] for name in required))
    if check.prerequisite_check_keys:
        lines.append("前置检查：" + "、".join(check.prerequisite_check_keys))
    for condition in check.prerequisite_answers:
        lines.append(
            "前置答案：" + condition.check_key + " 必须为 " + " / ".join(condition.allowed_values)
        )
    if stops:
        lines.append("停止条件：" + "、".join(conditions[name] for name in stops))
    if snapshot.handover_conditions:
        lines.append(
            "交接条件：" + "、".join(conditions[name] for name in snapshot.handover_conditions)
        )
    answer = check.answer_schema
    if answer.kind == "CHOICE":
        lines.append("反馈选项：" + "、".join(answer.options))
    elif answer.kind == "NUMBER":
        minimum = answer.minimum if answer.minimum is not None else "不限"
        maximum = answer.maximum if answer.maximum is not None else "不限"
        lines.append(f"数值范围：{minimum} 至 {maximum}")
    else:
        lines.append("反馈形式：" + ("是或否" if answer.kind == "BOOLEAN" else "文字说明"))
    return "\n".join(lines)


class RunReads:
    """快照不是授权，历史和SSE走同一来源复核路径。"""

    def __init__(
        self,
        engine: AsyncEngine,
        service: RunService,
        conversations: ConversationService,
        evidence: EvidenceService,
    ) -> None:
        """只复用现有资源和用例，不调用付费模型。"""
        self.engine, self.service, self.conversations, self.evidence = (
            engine,
            service,
            conversations,
            evidence,
        )

    async def list_for_conversation(
        self, actor: VerifiedIdentity, conversation_id: str, page: int, size: int
    ) -> PageView[RunView]:
        """执行历史按数据库分页，原始聊天另从消息游标读取。"""
        await self.conversations.authorized(actor, conversation_id)
        async with self.engine.connect() as db:
            count = (
                await db.execute(
                    text("SELECT COUNT(*) FROM ai_run WHERE conversation_id=:id"),
                    {"id": conversation_id},
                )
            ).scalar_one()
            ids = (
                (
                    await db.execute(
                        text(
                            "SELECT id FROM ai_run WHERE conversation_id=:id "
                            "ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset"
                        ),
                        {"id": conversation_id, "limit": size, "offset": (page - 1) * size},
                    )
                )
                .scalars()
                .all()
            )
        return PageView(
            records=[await self.get(actor, identifier) for identifier in ids],
            total=str(count),
            page_num=page,
            page_size=size,
        )

    async def get(
        self, actor: VerifiedIdentity, identifier: str, *, require_sources: bool = False
    ) -> RunView:
        """风险撤回或撤权隐藏所有派生正文，依赖故障保留明确错误。"""
        run = await self.service.authorized(actor, identifier)
        hidden = False
        try:
            evidence = await self.evidence.verify(run, actor.actor)
        except BusinessError as error:
            if require_sources:
                raise
            if error.error not in {CommonErrorCode.FORBIDDEN, EvidenceErrorCode.SOURCE_WITHDRAWN}:
                raise
            evidence = []
            hidden = True
        current_version = None
        if run.task_id:
            async with self.engine.connect() as db:
                task = await tasks.task(db, run.task_id)
            _, external = await self.service.access.authorize(actor, task)
            current_version = (
                external.diagnosis_context_version
                if run.context_manifest.ticket_id
                else task.diagnosis_context_version
            )
        async with self.engine.connect() as db:
            row = (
                (
                    await db.execute(
                        text("""
                SELECT r.output_message_id,r.output_state_version,r.structured_result,r.answer_type,
                    r.error_code,i.content AS question,o.content AS answer
                FROM ai_run r JOIN ai_message i ON i.id=r.input_message_id
                LEFT JOIN ai_message o ON o.id=r.output_message_id WHERE r.id=:id
            """),
                        {"id": identifier},
                    )
                )
                .mappings()
                .one()
            )
            metering = (
                await db.execute(
                    text("""
                SELECT input_tokens,output_tokens FROM ai_model_call WHERE run_id=:id
            """),
                    {"id": identifier},
                )
            ).all()
        structured = (
            RunResult.model_validate_json(row["structured_result"])
            if row["structured_result"] and not hidden
            else None
        )
        selected = set(structured.citation_numbers) if structured else set()
        citations = [
            CitationView(
                number=item.number,
                source_usage_id=item.id,
                title=item.title,
                locator=item.locator,
                excerpt=citation_excerpt(item),
                document_id=source.get("document_id")
                if isinstance(source := item.source_version.get("knowledge_source"), dict)
                else None,
            )
            for item in evidence
            if item.number in selected
        ]
        unknown = any(inputs is None or outputs is None for inputs, outputs in metering)
        return RunView(
            id=run.id,
            conversation_id=run.conversation_id,
            task_id=run.task_id,
            input_message_id=run.input_message_id,
            output_message_id=row["output_message_id"],
            status=run.status,
            execution_phase=run.execution_phase,
            iteration_number=run.iteration_number,
            question=row["question"],
            answer=None if hidden else row["answer"],
            answer_type=None if hidden else row["answer_type"],
            structured_result=structured,
            input_state_version=None
            if run.input_state_version is None
            else str(run.input_state_version),
            output_state_version=None
            if row["output_state_version"] is None
            else str(row["output_state_version"]),
            diagnosis_context_version=None
            if run.diagnosis_context_version is None
            else str(run.diagnosis_context_version),
            current_diagnosis_context_version=None
            if current_version is None
            else str(current_version),
            ticket_row_version=None
            if run.ticket_row_version is None
            else str(run.ticket_row_version),
            ticket_event_sequence=None
            if run.ticket_event_sequence is None
            else str(run.ticket_event_sequence),
            sources_hidden=hidden,
            safe_reason="引用资料已撤回或当前不可访问" if hidden else None,
            stale=current_version != run.diagnosis_context_version,
            citations=citations,
            input_tokens=None
            if any(row[0] is None for row in metering)
            else str(sum(row[0] for row in metering)),
            output_tokens=None
            if any(row[1] is None for row in metering)
            else str(sum(row[1] for row in metering)),
            usage_unknown=unknown,
            error_code=row["error_code"],
            created_at=instant(run.created_at),
            finished_at=instant(run.finished_at),
        )

    async def by_request(self, actor: VerifiedIdentity, key: str) -> RunView:
        """只按当前拥有者查原键；不凭未查到结果认定付费请求未发生。"""
        async with self.engine.connect() as db:
            old = await runs.receipt(db, actor.actor.owner_id, key)
        if old is None:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        return await self.get(actor, old.id)

    async def calls(self, actor: VerifiedIdentity, identifier: str) -> CallsView:
        """用量未知保持null，不暴露模型完整输入输出或工具参数。"""
        view = await self.get(actor, identifier)
        async with self.engine.connect() as db:
            models = (
                (
                    await db.execute(
                        text("""
                SELECT call_number,purpose,status,requested_model,response_model,input_tokens,
                    output_tokens,estimated_cost FROM ai_model_call
                WHERE run_id=:id ORDER BY call_number
            """),
                        {"id": identifier},
                    )
                )
                .mappings()
                .all()
            )
            tools = await self._tool_views(db, view)
        return CallsView(
            model_calls=[
                ModelCallView(
                    number=row["call_number"],
                    purpose=row["purpose"],
                    status=row["status"],
                    requested_model=row["requested_model"],
                    response_model=row["response_model"],
                    input_tokens=None if row["input_tokens"] is None else str(row["input_tokens"]),
                    output_tokens=None
                    if row["output_tokens"] is None
                    else str(row["output_tokens"]),
                    estimated_cost=None
                    if row["estimated_cost"] is None
                    else str(row["estimated_cost"]),
                )
                for row in models
            ],
            tool_calls=tools,
        )

    async def subscription_snapshot(
        self, actor: VerifiedIdentity, identifier: str
    ) -> tuple[RunView, list[ToolCallView], RunTaskUpdate | None]:
        """一次订阅轮询只复核一次运行及来源，只读取需交付的工具和任务状态。"""
        view = await self.get(actor, identifier)
        async with self.engine.connect() as db:
            tools = await self._tool_views(db, view)
            update = None
            if (
                view.status == "SUCCEEDED"
                and view.task_id
                and view.output_state_version is not None
                and not view.sources_hidden
            ):
                details = await tasks.completed_run_event(
                    db, view.task_id, int(view.output_state_version)
                )
                if details is not None:
                    update = RunTaskUpdate(
                        task_id=view.task_id,
                        status=details.receipt.status,
                        task_state_version=view.output_state_version,
                        pending_check_ids=details.changed_check_ids,
                    )
        return view, tools, update

    async def _tool_views(self, db: AsyncConnection, view: RunView) -> list[ToolCallView]:
        """同次授权读取的工具状态，来源隐藏时去除摘要和引用。"""
        rows = (
            (
                await db.execute(
                    text("""
            SELECT tool_number,tool_name,status,result_summary,result_refs
            FROM ai_tool_call WHERE run_id=:id ORDER BY tool_number
        """),
                    {"id": view.id},
                )
            )
            .mappings()
            .all()
        )
        return [
            ToolCallView(
                number=row["tool_number"],
                name=row["tool_name"],
                status=row["status"],
                result_summary=None if view.sources_hidden else row["result_summary"],
                source_usage_ids=[]
                if view.sources_hidden or not row["result_refs"]
                else json.loads(row["result_refs"]).get("source_usage_ids", []),
            )
            for row in rows
        ]

    async def messages(
        self, actor: VerifiedIdentity, conversation_id: str, after: int, limit: int
    ) -> MessagePage:
        """消息有界增量查询，助手来源失效不影响用户自己原始反馈读取。"""
        await self.conversations.authorized(actor, conversation_id)
        async with self.engine.connect() as db:
            rows = (
                (
                    await db.execute(
                        text("""
                SELECT m.id,m.task_id,m.sequence_number,m.role,m.content,m.created_at,r.id AS run_id
                FROM ai_message m LEFT JOIN ai_run r ON r.output_message_id=m.id
                WHERE m.conversation_id=:conversation AND m.sequence_number>:after
                ORDER BY m.sequence_number LIMIT :limit
            """),
                        {"conversation": conversation_id, "after": after, "limit": limit + 1},
                    )
                )
                .mappings()
                .all()
            )
        records = []
        used = 0
        for row in rows[:limit]:
            content = row["content"]
            hidden = False
            if row["role"] == "ASSISTANT":
                if row["run_id"] is None:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                output = await self.get(actor, row["run_id"])
                content, hidden = output.answer, output.sources_hidden
            item = MessageView(
                id=row["id"],
                task_id=row["task_id"],
                sequence_number=str(row["sequence_number"]),
                role=row["role"],
                content=content,
                run_id=row["run_id"],
                sources_hidden=hidden,
                created_at=instant(row["created_at"]),
            )
            cost = len(item.model_dump_json().encode())
            if used + cost > 128 * 1024:
                break
            used += cost
            records.append(item)
        return MessagePage(
            records=records,
            next_cursor=records[-1].sequence_number if records else str(after),
            has_more=len(rows) > len(records),
        )
