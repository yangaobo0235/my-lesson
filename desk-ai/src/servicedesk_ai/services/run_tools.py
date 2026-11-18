"""固定业务范围的只读工具注册、持久调用和无进展保护。作者：yangaobo。"""

from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass

import grpc
from pydantic import ValidationError
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine

from desk_contracts.ticket.v1.support_pb2 import AttachmentListRequest, TicketEventsRequest
from servicedesk_ai.diagnosis.budget import ExecutionBudgetExceeded
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc
from servicedesk_ai.infrastructure.identity import IdentityFailure, translate_failure
from servicedesk_ai.infrastructure.index_replicas import IndexReplicas
from servicedesk_ai.infrastructure.models import (
    ModelMessage,
    ModelTool,
    ModelToolCall,
    compact_schema,
)
from servicedesk_ai.repositories import runs, tasks
from servicedesk_ai.repositories.values import json_value, new_id
from servicedesk_ai.schemas.diagnosis import (
    InspectDiagnosticText,
    ListDiagnosticAttachments,
    ReadEvidence,
    ReadTaskContext,
    ReadTicketEvents,
    SearchKnowledge,
)
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.schemas.procedures import ReadProcedure
from servicedesk_ai.schemas.runs import ReusedCall
from servicedesk_ai.services.evidence import Evidence, EvidenceService, actor_ref, source_query
from servicedesk_ai.services.model_calls import ModelCalls


def tool_failure_status(error: BaseException) -> str:
    """确定拒绝不冒充未知效果；执行权/环境变化与取消必须终止当前运行。"""
    if isinstance(error, asyncio.CancelledError):
        return "CANCELLED"
    if isinstance(error, grpc.aio.AioRpcError):
        error = translate_failure(error)
    code = (
        error.error.code
        if isinstance(error, BusinessError)
        else error.code
        if isinstance(error, IdentityFailure)
        else None
    )
    if code == CommonErrorCode.INVALID_REQUEST.code:
        return "INVALID_ARGUMENT"
    if code in {CommonErrorCode.FORBIDDEN.code, CommonErrorCode.NOT_FOUND.code}:
        return "FORBIDDEN"
    if code == CommonErrorCode.CONFLICT.code:
        return "STALE_CONTEXT"
    if code in {
        CommonErrorCode.SERVICE_UNAVAILABLE.code,
        CommonErrorCode.DEPENDENCY_TIMEOUT.code,
        CommonErrorCode.UPSTREAM_PROTOCOL_ERROR.code,
    } or isinstance(error, TimeoutError):
        return "DEPENDENCY_FAILED"
    return "UNKNOWN"


def knowledge_candidates(
    items: list[Evidence], messages: list[ModelMessage] | None = None
) -> list[dict[str, object]]:
    """恢复未在当前工具回执展示的历史定位，避免重复目录挤占正文预算。"""
    presented: set[int] = set()
    for message in messages or []:
        if message.role == "tool":
            values = json.loads(message.content or "[]")
            for value in values:
                if isinstance(value, dict) and isinstance(value.get("sourceNumber"), int):
                    presented.add(value["sourceNumber"])
    candidates: dict[tuple[object, object], dict[str, object]] = {}
    for item in items:
        if (
            item.usage_kind != "METADATA"
            or item.source_type not in {"CHUNK", "PROCEDURE_CHECK"}
            or item.number in presented
        ):
            continue
        source = item.source_version.get("knowledge_source")
        if not isinstance(source, dict):
            continue
        document_id = source.get("document_id")
        if not document_id:
            continue
        procedure_keys = source.get("procedure_check_keys", [])
        # 审核检查由文档和检查键定位；同修订多个片段不需要重复整份目录。
        key = (document_id, None if procedure_keys else item.chunk_id)
        candidates[key] = {
            "documentId": document_id,
            "title": item.title,
            "procedureCheckKeys": procedure_keys,
            **({"chunkId": item.chunk_id} if item.chunk_id and not procedure_keys else {}),
        }
    return list(candidates.values())[:8]


@dataclass(frozen=True)
class PreparedTool:
    """每次模型提议独立记账；重复请求只关联原序号，不重新调用外部依赖。"""

    identifier: str
    repeated_number: int | None
    invalid_arguments: bool = False


class RunTools:
    """模型只选择声明工具及参数，客户、任务、工单和执行权全部由宿主固定。"""

    def __init__(
        self,
        engine: AsyncEngine,
        rpc: DomainRpc,
        replicas: IndexReplicas,
        evidence: EvidenceService,
        models: ModelCalls,
    ) -> None:
        """工具不持有工单写接口，生命周期随worker连接作用域。"""
        self.engine, self.rpc, self.replicas, self.evidence = engine, rpc, replicas, evidence
        self.models = models

    def definitions(self, run: runs.RunLease) -> list[ModelTool]:
        """咨询无业务工具；未分类任务只允许澄清，不做无范围召回。"""
        if run.task_id is None or run.context_manifest.service_item_id is None:
            return []
        definitions = [
            ModelTool(
                name="load_procedure",
                description="读取单项审核检查及完整全局安全条件，可独立作为该检查的依据；不能自行改写规程",
                parameters=ReadProcedure.model_json_schema(by_alias=True),
            ),
            ModelTool(
                name="search_knowledge",
                description="搜索有权知识；指定资料标题时填documentTitle精确定位，空结果不得改引其他标题冒充完成。预览须再load_procedure或read_evidence",
                parameters=SearchKnowledge.model_json_schema(by_alias=True),
            ),
            ModelTool(
                name="read_evidence",
                description="首次offset=0阅读候选章节；只用回执chunkId和nextOffset续页，endOffset不是续页游标。无nextOffset表示本章读完，不表示整篇读完；有nextSectionId才读下一段",
                parameters=ReadEvidence.model_json_schema(by_alias=True),
            ),
        ]
        definitions.append(
            ModelTool(
                name="read_task_context",
                description="仅当需要未提供的事实或检查时补读当前任务；不要重读已给出的检查反馈。query为字面子串，nextBeforeId续页，旧环境会标明",
                parameters=ReadTaskContext.model_json_schema(by_alias=True),
            )
        )
        if run.context_manifest.ticket_id:
            definitions.extend(
                [
                    ModelTool(
                        name="list_diagnostic_attachments",
                        description="发现绑定工单允许AI使用的脱敏附件；元信息不代表已读正文",
                        parameters=ListDiagnosticAttachments.model_json_schema(by_alias=True),
                    ),
                    ModelTool(
                        name="inspect_diagnostic_text",
                        description="按固定脱敏版本读取诊断文本，返回字符定位及下一页；不执行内容",
                        parameters=InspectDiagnosticText.model_json_schema(by_alias=True),
                    ),
                ]
            )
            definitions.append(
                ModelTool(
                    name="read_ticket_events",
                    description="读取绑定工单的有权正式记录",
                    parameters=ReadTicketEvents.model_json_schema(by_alias=True),
                )
            )
        return [
            tool.model_copy(update={"parameters": compact_schema(tool.parameters)})
            for tool in definitions
        ]

    @staticmethod
    def evidence_message(item: Evidence) -> dict[str, object]:
        """展示真实来源号与摘录，预览不冒充完整知识正文。"""
        source = item.source_version.get("knowledge_source")
        value = {
            "sourceNumber": item.number,
            "sourceType": item.source_type,
            "usageKind": item.usage_kind,
            "title": item.title,
            "content": item.excerpt,
            "factId": item.fact_id,
            "messageId": item.message_id,
            "messageRole": item.source_version.get("message_role", "UNKNOWN")
            if item.source_type == "MESSAGE"
            else None,
            "factSourceType": item.source_version.get("fact_source_type", "UNKNOWN")
            if item.source_type == "FACT"
            else None,
            "complete": item.usage_kind == "CONTENT"
            and item.source_version.get("content_complete", True),
            "offset": item.source_version.get("content_offset"),
            "endOffset": item.source_version.get("content_end"),
            "totalCharacters": item.source_version.get("content_length"),
            "chunkId": item.chunk_id,
            "documentId": source.get("document_id") if isinstance(source, dict) else None,
            "procedureCheckKeys": source.get("procedure_check_keys", [])
            if isinstance(source, dict)
            else [],
            "sectionPart": item.source_version.get("section_part"),
            "sectionParts": item.source_version.get("section_parts"),
            "previousSectionId": item.source_version.get("previous_section_id"),
            "nextSectionId": item.source_version.get("next_section_id"),
            "attachmentId": item.attachment_id,
            "redactionVersion": str(item.source_version["redaction_version"])
            if item.attachment_id
            else None,
            "nextOffset": item.source_version.get("next_offset")
            if item.usage_kind == "CONTENT"
            else None,
            "readFromOffset": 0
            if item.source_type == "CHUNK" and item.usage_kind == "METADATA"
            else None,
        }
        if item.usage_kind == "METADATA":
            for name in ("offset", "endOffset", "totalCharacters"):
                value.pop(name, None)
        elif item.source_type == "CHUNK":
            value["contentScope"] = "SECTION"
            value["scopeNotice"] = "complete仅表示本章节读完，不证明整篇文档已读或没有其他内容。"
        # 不重复发送与当前来源无关的空字段；真实定位和继续阅读身份仍完整保留。
        return {key: item for key, item in value.items() if item is not None and item != []}

    async def prepare(
        self, run: runs.RunLease, model_call_id: str, call: ModelToolCall
    ) -> PreparedTool:
        """持久意图先于外调；同轮相同请求记为重复提议，不能误报环境版本改变。"""
        if call.name not in {tool.name for tool in self.definitions(run)}:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        parameter_type = (
            SearchKnowledge
            if call.name == "search_knowledge"
            else ReadEvidence
            if call.name == "read_evidence"
            else ReadProcedure
            if call.name == "load_procedure"
            else ReadTaskContext
            if call.name == "read_task_context"
            else ListDiagnosticAttachments
            if call.name == "list_diagnostic_attachments"
            else InspectDiagnosticText
            if call.name == "inspect_diagnostic_text"
            else ReadTicketEvents
        )
        invalid_arguments = False
        try:
            parsed = parameter_type.model_validate(call.arguments)
            arguments = {"schema_version": 1, **parsed.model_dump(mode="json")}
        except ValidationError:
            # 保存原提议以便审计和重复保护；不钳制参数，也不把校验异常正文发给模型。
            invalid_arguments = True
            arguments = {"schema_version": 1, "invalid_arguments": call.arguments}
        fingerprint = canonical_hash(
            {"tool": call.name, "arguments": arguments, "context": run.diagnosis_context_version}
        )
        async with self.engine.begin() as db:
            await runs.owned(db, run)
            previous = (
                await db.execute(
                    text("""
                SELECT COUNT(*),COALESCE(MAX(tool_number),0) FROM ai_tool_call WHERE run_id=:id
            """),
                    {"id": run.id},
                )
            ).one()
            if previous[0] >= run.pipeline_config.max_tool_calls:
                raise ExecutionBudgetExceeded("本轮工具调用次数已达到预算")
            duplicate = (
                await db.execute(
                    text("""
                SELECT tool_number FROM ai_tool_call WHERE run_id=:run AND input_fingerprint=:hash
                ORDER BY tool_number LIMIT 1
            """),
                    {"run": run.id, "hash": fingerprint},
                )
            ).scalar_one_or_none()
            identifier = new_id()
            await db.execute(
                text("""
                INSERT INTO ai_tool_call(id,run_id,model_call_id,provider_tool_call_id,tool_number,
                    tool_name,arguments,input_fingerprint,claim_token)
                VALUES(:id,:run,:model,:provider,:number,:name,:arguments,:hash,:token)
            """),
                {
                    "id": identifier,
                    "run": run.id,
                    "model": model_call_id,
                    "provider": call.id,
                    "number": previous[1] + 1,
                    "name": call.name,
                    "arguments": json_value(arguments),
                    "hash": fingerprint,
                    "token": run.claim_token,
                },
            )
        return PreparedTool(identifier, duplicate, invalid_arguments)

    async def execute(
        self, run: runs.RunLease, model_call_id: str, call: ModelToolCall
    ) -> list[dict[str, object]]:
        """工具状态和结果来源有独立身份，外调结果在父执行权有效时才接受。"""
        prepared = await self.prepare(run, model_call_id, call)
        identifier = prepared.identifier
        async with self.engine.begin() as db:
            await runs.owned(db, run)
            await db.execute(
                text("""
                UPDATE ai_tool_call SET status='RUNNING',started_at=UTC_TIMESTAMP(6) WHERE id=:id
            """),
                {"id": identifier},
            )
            await db.execute(
                text("UPDATE ai_run SET execution_phase='TOOL' WHERE id=:id"), {"id": run.id}
            )
        try:
            items: list[Evidence] = []
            reused: ReusedCall | None = None
            if prepared.repeated_number is not None:
                result = [
                    {
                        "status": "DUPLICATE_CALL",
                        "previousToolNumber": prepared.repeated_number,
                        "notice": (
                            "本轮相同请求已登记，未再次外调。请根据已返回的回执完成本轮；"
                            "没有成功回执的请求不能作为证据。宿主停止继续取证，避免循环。"
                        ),
                    }
                ]
            elif prepared.invalid_arguments:
                result = [
                    {
                        "status": "INVALID_ARGUMENT",
                        "notice": "参数不符合该工具的Schema，未读取外部数据或取得证据。"
                        "请按已提供的字段、类型和范围纠正参数；纠正仍计入本轮调用预算。",
                    }
                ]
            elif call.name == "list_diagnostic_attachments":
                result = await self.list_attachments(run, call)
            elif call.name == "read_task_context":
                items, result = await self.task_context(
                    run, ReadTaskContext.model_validate(call.arguments)
                )
            else:
                cached = await self.reuse_knowledge(run, call)
                if cached is None:
                    items = await self.read(run, call)
                else:
                    items, reused = cached
                result = [self.evidence_message(item) for item in items]
            payload = {"schema_version": 1, "items": result}
            raw = json_value(payload)
            if len(raw.encode()) > 64 * 1024:
                raise BusinessError(CommonErrorCode.PAYLOAD_TOO_LARGE)
            async with self.engine.begin() as db:
                await runs.owned(db, run)
                if reused is not None:
                    current = await runs.run(db, run.id)
                    manifest = current.context_manifest.model_copy(deep=True)
                    if reused not in manifest.reused_calls:
                        if len(manifest.reused_calls) >= 32:
                            raise BusinessError(CommonErrorCode.CONFLICT)
                        manifest.reused_calls.append(reused)
                    await db.execute(
                        text("UPDATE ai_run SET context_manifest=:value WHERE id=:id"),
                        {"id": run.id, "value": manifest.model_dump_json()},
                    )
                await db.execute(
                    text("""
                    UPDATE ai_tool_call SET status=:status,result_summary=:summary,
                        result_payload=:payload,
                        result_refs=:refs,result_fingerprint=:hash,finished_at=UTC_TIMESTAMP(6)
                    WHERE id=:id AND status='RUNNING' AND claim_token=:token
                """),
                    {
                        "id": identifier,
                        "status": "INVALID_ARGUMENT"
                        if prepared.repeated_number is not None or prepared.invalid_arguments
                        else "OK"
                        if result
                        else "EMPTY",
                        "summary": (
                            "重复请求已阻断，未再次外调或登记新证据"
                            if prepared.repeated_number is not None
                            else "参数校验拒绝，未读取数据或登记来源"
                            if prepared.invalid_arguments
                            else (
                                f"返回{len(result)}项结果，登记{len(items)}项来源依赖；没有执行修复"
                            )
                        ),
                        "payload": raw,
                        "refs": json_value(
                            {"schema_version": 1, "source_usage_ids": [item.id for item in items]}
                        ),
                        "hash": canonical_hash(payload),
                        "token": run.claim_token,
                    },
                )
            return result
        except (Exception, asyncio.CancelledError) as error:
            status = tool_failure_status(error)
            result = [
                {
                    "status": status,
                    "notice": (
                        "工具没有返回证据，不能据此推断业务事实。仅使用已提供的有效参数和候选身份；"
                        "没有procedureCheckKeys的资料不支持load_procedure，请按需read_evidence。"
                    ),
                }
            ]
            payload = {"schema_version": 1, "items": result}
            async with self.engine.begin() as db:
                try:
                    await runs.owned(db, run)
                except BusinessError:
                    pass
                else:
                    await db.execute(
                        text("""
                        UPDATE ai_tool_call SET status=:status,result_summary=:summary,
                            result_payload=:payload,result_fingerprint=:hash,
                            finished_at=UTC_TIMESTAMP(6)
                        WHERE id=:id AND status='RUNNING' AND claim_token=:token
                    """),
                        {
                            "id": identifier,
                            "token": run.claim_token,
                            "status": status,
                            "summary": status + "：未取得证据",
                            "payload": json_value(payload),
                            "hash": canonical_hash(payload),
                        },
                    )
                    if status in {"INVALID_ARGUMENT", "FORBIDDEN", "DEPENDENCY_FAILED"}:
                        return result
            raise

    async def task_context(
        self,
        run: runs.RunLease,
        form: ReadTaskContext,
    ) -> tuple[list[Evidence], list[dict[str, object]]]:
        """补读不是重新执行；原任务状态、实际来源及传递撤权都必须重核。"""
        if run.task_id is None or run.input_state_version is None:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        async with self.engine.connect() as db:
            task = await tasks.task(db, run.task_id)
            if task.task_state_version != run.input_state_version:
                raise BusinessError(CommonErrorCode.CONFLICT)
            rows = (
                await tasks.context_facts(
                    db,
                    run.task_id,
                    run.input_state_version,
                    form.before_id,
                    form.query,
                    form.limit + 1,
                )
                if form.section == "FACTS"
                else await tasks.context_checks(
                    db, run.task_id, form.before_id, form.query, form.limit + 1
                )
            )
        items: dict[str, Evidence] = {}
        records = []
        for row in rows[: form.limit]:
            represented = (
                any(
                    reference.id == row["id"] and reference.state_version == row["state_version"]
                    for reference in run.context_manifest.facts
                )
                if form.section == "FACTS"
                else any(
                    reference.id == row["id"]
                    and reference.row_version == row["row_version"]
                    and reference.context_version == row["context_version"]
                    for reference in run.context_manifest.checks
                )
            )
            if represented:
                # 初始输入已经由messages按预算筛选并固定版本；只复用该真实输入，不查旧摘要猜测。
                records.append(
                    {
                        "factId" if form.section == "FACTS" else "checkId": row["id"],
                        "alreadyInInitialContext": True,
                        **({"instruction": row["instruction"]} if form.section == "CHECKS" else {}),
                        "notice": "同身份及版本的完整事实或检查反馈已在初始输入中。",
                    }
                )
                continue
            if form.section == "FACTS":
                if row["source_type"] == "TOOL_OBSERVATION":
                    if row["source_run_id"] is None:
                        raise BusinessError(CommonErrorCode.FORBIDDEN)
                    for item in await self.evidence.inherit(run, row["source_run_id"]):
                        items[item.id] = item
                environment = json.loads(row["environment_snapshot"])
                async with self.engine.begin() as db:
                    item = await self.evidence.add(
                        db,
                        run,
                        source_type="FACT",
                        title="诊断事实 · " + row["source_type"],
                        excerpt=row["statement"],
                        fact_id=row["id"],
                        locator={
                            "schema_version": 1,
                            "kind": "DIAGNOSTIC_FACT",
                            "fact_id": row["id"],
                        },
                        source_version={
                            "state_version": row["state_version"],
                            "fact_source_type": row["source_type"],
                            "environment_snapshot": environment,
                        },
                    )
                items[item.id] = item
                records.append(
                    {
                        "factId": row["id"],
                        "statement": row["statement"],
                        "factKind": row["fact_kind"],
                        "stateVersion": str(row["state_version"]),
                        "recordedAt": row["created_at"].isoformat(),
                        "factSourceType": row["source_type"],
                        "sourceNumber": item.number,
                        "historicalEnvironment": environment["diagnosis_context_version"]
                        != run.diagnosis_context_version,
                    }
                )
            else:
                for item in await self.evidence.inherit(run, row["proposed_run_id"]):
                    items[item.id] = item
                answer_numbers = []
                if row["answer_message_id"]:
                    async with self.engine.begin() as db:
                        item = await self.evidence.check_feedback(db, run, row)
                    items[item.id] = item
                    answer_numbers.append(item.number)
                # 检查仅恢复已发生的提问/反馈，不将旧操作片段提升为当前完整规程。
                records.append(
                    {
                        "checkId": row["id"],
                        "checkKey": row["check_key"],
                        "checkKind": row["check_kind"],
                        "instruction": row["instruction"],
                        "status": row["status"],
                        "proposedRunId": row["proposed_run_id"],
                        "rowVersion": str(row["row_version"]),
                        "feedbackSourceNumbers": answer_numbers,
                        "answeredAt": row["answered_at"].isoformat()
                        if row["answered_at"]
                        else None,
                        "normalizedAnswer": json.loads(row["normalized_answer"])
                        if row["normalized_answer"]
                        else None,
                        "historicalEnvironment": row["context_version"]
                        != run.diagnosis_context_version,
                        "notice": "历史检查与反馈不是当前操作依据，操作仍须读取有权资料",
                    }
                )
        async with self.engine.connect() as db:
            if (await tasks.task(db, run.task_id)).task_state_version != run.input_state_version:
                raise BusinessError(CommonErrorCode.CONFLICT)
        await self.evidence.verify(run)
        return list(items.values()), [
            {
                "section": form.section,
                "records": records,
                "hasMore": len(rows) > form.limit,
                "nextBeforeId": rows[form.limit - 1]["id"] if len(rows) > form.limit else None,
            }
        ]

    async def no_progress(self, run: runs.RunLease) -> bool:
        """按本轮有界持久回执判断连续三次取证是否没有新增依据。

        完整来源使用本轮来源身份；附件目录使用脱敏版本，补读检查使用行版本。
        初始上下文和较早回执已包含的身份不算新进展，空结果和拒绝同样不算。
        资料正文、模型自述和游标变化不能重置计数；父执行权失效仍直接失败。
        """
        async with self.engine.begin() as db:
            await runs.owned(db, run)
            rows = list(
                (
                    await db.execute(
                        text("""
                        SELECT tool_name,status,result_payload,result_refs FROM ai_tool_call
                        WHERE run_id=:run ORDER BY tool_number LIMIT :limit
                    """),
                        {"run": run.id, "limit": run.pipeline_config.max_tool_calls},
                    )
                ).mappings()
            )
        known = {("source", identifier, "") for identifier in run.context_manifest.source_usages}
        known.update(
            ("check", reference.id, str(reference.row_version))
            for reference in run.context_manifest.checks
        )
        streak = 0
        for row in rows:
            if row["status"] in {"PREPARED", "RUNNING", "UNKNOWN"}:
                raise BusinessError(CommonErrorCode.CONFLICT)
            observed: set[tuple[str, str, str]] = set()
            if row["status"] == "OK":
                refs = json.loads(row["result_refs"]) if row["result_refs"] else {}
                observed.update(("source", value, "") for value in refs.get("source_usage_ids", []))
                payload = json.loads(row["result_payload"])
                for item in payload["items"]:
                    if row["tool_name"] == "list_diagnostic_attachments":
                        observed.update(
                            ("attachment", record["attachmentId"], record["redactionVersion"])
                            for record in item["items"]
                        )
                    elif row["tool_name"] == "read_task_context" and item["section"] == "CHECKS":
                        observed.update(
                            ("check", record["checkId"], record["rowVersion"])
                            for record in item["records"]
                            if not record.get("alreadyInInitialContext")
                        )
            streak = 0 if observed - known else streak + 1
            known.update(observed)
        return streak >= 3

    async def reuse_knowledge(
        self, run: runs.RunLease, call: ModelToolCall
    ) -> tuple[list[Evidence], ReusedCall] | None:
        """显式新Run只复用同任务同环境的完整知识读取回执；发现和易变业务始终重读。"""
        if call.name != "read_evidence" or run.task_id is None:
            return None
        form = ReadEvidence.model_validate(call.arguments)
        arguments = {"schema_version": 1, **form.model_dump(mode="json")}
        fingerprint = canonical_hash(
            {"tool": call.name, "arguments": arguments, "context": run.diagnosis_context_version}
        )
        async with self.engine.connect() as db:
            row = (
                (
                    await db.execute(
                        text("""
                SELECT c.id,c.run_id,c.result_payload,c.result_refs,c.result_fingerprint
                FROM ai_tool_call c JOIN ai_run r ON r.id=c.run_id
                WHERE r.task_id=:task AND r.conversation_id=:conversation AND r.owner_id=:owner
                  AND r.id<>:run AND r.diagnosis_context_version=:context
                  AND r.status IN ('SUCCEEDED','FAILED','INTERRUPTED','CANCELLED')
                  AND JSON_UNQUOTE(r.pipeline_config->'$.tool_version')=:tool_version
                  AND c.tool_name='read_evidence' AND c.input_fingerprint=:hash AND c.status='OK'
                ORDER BY c.finished_at DESC,c.id LIMIT 1
            """),
                        {
                            "task": run.task_id,
                            "conversation": run.conversation_id,
                            "owner": run.owner_id,
                            "run": run.id,
                            "context": run.diagnosis_context_version,
                            "tool_version": run.pipeline_config.tool_version,
                            "hash": fingerprint,
                        },
                    )
                )
                .mappings()
                .first()
            )
            if row is None:
                return None
            parent = await runs.run(db, row["run_id"])
        self.evidence.same_scope(run, parent)
        current = await self.evidence.list_items(run.id)
        candidates = {
            value
            for item in current
            if item.source_type == "CHUNK"
            for value in (
                item.chunk_id,
                item.source_version.get("previous_section_id"),
                item.source_version.get("next_section_id"),
            )
        }
        if form.chunk_id not in candidates:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        payload = json.loads(row["result_payload"])
        refs = json.loads(row["result_refs"])["source_usage_ids"]
        if (
            not refs
            or len(set(refs)) != len(refs)
            or canonical_hash(payload) != row["result_fingerprint"]
        ):
            raise BusinessError(CommonErrorCode.CONFLICT)
        originals = {item.id: item for item in await self.evidence.list_items(parent.id)}
        if any(ref not in originals for ref in refs):
            raise BusinessError(CommonErrorCode.CONFLICT)
        selected = [originals[ref] for ref in refs]
        if any(item.source_type != "CHUNK" for item in selected) or payload != {
            "schema_version": 1,
            "items": [self.evidence_message(item) for item in selected],
        }:
            raise BusinessError(CommonErrorCode.CONFLICT)
        allowed = {
            item.index_job_id for item in (await self.rpc.sources(source_query(run))).sources
        }
        for item in selected:
            source = item.source_version.get("knowledge_source")
            if not isinstance(source, dict) or source.get("index_job_id") not in allowed:
                # 普通换版后不把旧回执当作当前发现；正常读取负责返回当前权限结果。
                return None
        await self.evidence.verify_items(parent, selected, actor_ref(run), set())
        copied = []
        async with self.engine.begin() as db:
            for item in selected:
                copied.append(
                    await self.evidence.add(
                        db,
                        run,
                        source_type=item.source_type,
                        title=item.title,
                        excerpt=item.excerpt,
                        locator=item.locator,
                        source_version=item.source_version,
                        chunk_id=item.chunk_id,
                    )
                )
        return copied, ReusedCall(call_type="TOOL", call_id=row["id"], run_id=parent.id)

    async def list_attachments(
        self, run: runs.RunLease, call: ModelToolCall
    ) -> list[dict[str, object]]:
        """只发现当前工单有权元信息，候选不会登记成实际正文引用。"""
        form = ListDiagnosticAttachments.model_validate(call.arguments)
        if not run.context_manifest.ticket_id:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        request = AttachmentListRequest(
            actor=actor_ref(run), ticket_id=run.context_manifest.ticket_id
        )
        if form.after_id:
            request.after_id = form.after_id
        response = await self.rpc.attachments.ListRedacted(request, **self.rpc.options())
        return [
            {
                "items": [
                    {
                        "attachmentId": item.id,
                        "name": item.name,
                        "contentFormat": item.content_format,
                        "redactionVersion": str(item.redaction_version),
                        "sha256": item.sha256,
                    }
                    for item in response.items
                ],
                "nextAfterId": response.next_after_id
                if response.HasField("next_after_id")
                else None,
            }
        ]

    async def read(self, run: runs.RunLease, call: ModelToolCall) -> list[Evidence]:
        """每次读取重新授权；模型提供的片段必须已经出现在本轮候选。"""
        if call.name == "inspect_diagnostic_text":
            attachment = InspectDiagnosticText.model_validate(call.arguments)
            return await self.evidence.diagnostic_text(
                run, attachment.attachment_id, int(attachment.redaction_version), attachment.offset
            )
        if call.name == "load_procedure":
            procedure = ReadProcedure.model_validate(call.arguments)
            seen = False
            for item in await self.evidence.list_items(run.id):
                source = item.source_version.get("knowledge_source")
                if isinstance(source, dict) and source.get("document_id") == procedure.document_id:
                    seen |= procedure.check_key in source.get("procedure_check_keys", [])
            if not seen:
                raise BusinessError(CommonErrorCode.FORBIDDEN)
            return await self.evidence.procedure(run, procedure.document_id, procedure.check_key)
        if call.name == "search_knowledge":
            search = SearchKnowledge.model_validate(call.arguments)
            allowed = list((await self.rpc.sources(source_query(run))).sources)
            if search.document_title is not None:
                allowed = [item for item in allowed if item.title == search.document_title]
            if search.source_type:
                allowed = [item for item in allowed if item.source_type == search.source_type]
            if not allowed:
                return []
            # 查询Embedding只输入query，不把此前已读事实伪记为其模型输入。
            embedding = await self.models.embed(run, [search.query])
            identifiers = await self.replicas.search(
                search.query,
                [item.index_job_id for item in allowed],
                search.limit,
                vector=embedding.vectors[0],
                candidates_per_source=run.pipeline_config.candidates_per_source,
                rrf_k=run.pipeline_config.rrf_k,
                evidence_limit=run.pipeline_config.evidence_limit,
            )
            return await self.evidence.knowledge(run, identifiers, allowed)
        if call.name == "read_evidence":
            read = ReadEvidence.model_validate(call.arguments)
            candidates: set[object] = set()
            for item in await self.evidence.list_items(run.id):
                if item.source_type == "CHUNK":
                    candidates.update(
                        (
                            item.chunk_id,
                            item.source_version.get("previous_section_id"),
                            item.source_version.get("next_section_id"),
                        )
                    )
            if read.chunk_id not in candidates:
                raise BusinessError(CommonErrorCode.FORBIDDEN)
            allowed = list((await self.rpc.sources(source_query(run))).sources)
            return await self.evidence.knowledge(
                run,
                [read.chunk_id],
                allowed,
                complete=True,
                expand_section=read.expand_section,
                offset=read.offset,
            )
        read_events = ReadTicketEvents.model_validate(call.arguments)
        request = TicketEventsRequest(
            actor=actor_ref(run),
            ticket_id=run.context_manifest.ticket_id,
            event_ids=read_events.event_ids,
            limit=read_events.limit,
        )
        if read_events.after_sequence is not None:
            request.after_sequence = read_events.after_sequence
        response = await self.rpc.ticket.ReadTicketEvents(request, **self.rpc.options())
        result = []
        async with self.engine.begin() as db:
            for event in response.events:
                result.append(
                    await self.evidence.add(
                        db,
                        run,
                        source_type="TICKET_EVENT",
                        title="工单记录 · " + event.event_type,
                        excerpt=event.content,
                        locator={
                            "schema_version": 1,
                            "kind": "TICKET_EVENT",
                            "event_id": event.id,
                            "sequence": event.sequence,
                        },
                        source_version={"ticket_row_version": run.ticket_row_version},
                        ticket_event_id=event.id,
                    )
                )
        return result
