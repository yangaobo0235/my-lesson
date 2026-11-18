"""实际上下文来源、传递依赖及交付前复核，候选集不自动成为证据。作者：yangaobo。"""

from __future__ import annotations

import json
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from typing import Literal

import grpc
from google.protobuf.json_format import MessageToDict, ParseDict
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine

from desk_contracts.common.v1.common_pb2 import ActorRef
from desk_contracts.knowledge.v1.source_pb2 import KnowledgeSource, ProcedureRequest, SourceQuery
from desk_contracts.ticket.v1.support_pb2 import Environment, RedactedRequest, VerifyTicketRequest
from servicedesk_ai.diagnosis.budget import estimate_text_tokens
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.evidence import EvidenceErrorCode
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc
from servicedesk_ai.infrastructure.identity import translate_failure
from servicedesk_ai.repositories import evidence as queries
from servicedesk_ai.repositories.runs import RunLease, owned
from servicedesk_ai.repositories.runs import run as read_run
from servicedesk_ai.repositories.values import json_value, new_id
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.schemas.procedures import ProcedureDefinition, ProcedureSnapshot


def complete_knowledge_numbers(rows: Sequence[Mapping]) -> set[int]:
    """只有同一摘要的正文区间从0连续覆盖末尾，才能作为完整操作依据。"""
    complete: set[int] = set()
    pages: dict[tuple[str, str, int], list[tuple[int, int, int]]] = {}
    for row in rows:
        if row["source_type"] != "CHUNK" or row["usage_kind"] != "CONTENT":
            continue
        version = json.loads(row["source_version"])
        if version.get("content_complete") is True:
            complete.add(row["source_number"])
        start, end, size = (
            version.get(key) for key in ("content_offset", "content_end", "content_length")
        )
        digest = version.get("content_sha256")
        if (
            isinstance(digest, str)
            and all(type(v) is int for v in (start, end, size))
            and 0 <= start < end <= size
        ):
            pages.setdefault((row["chunk_id"], digest, size), []).append(
                (start, end, row["source_number"])
            )
    for (_, _, size), spans in pages.items():
        cursor = 0
        for start, end, _ in sorted(spans):
            if start > cursor:
                break
            cursor = max(cursor, end)
        if cursor == size:
            complete.update(number for _, _, number in spans)
    return complete


@dataclass(frozen=True)
class Evidence:
    """限长实际摘录及真实定位；来源ID与引用编号相互独立。"""

    id: str
    number: int
    source_type: str
    title: str
    excerpt: str
    locator: dict[str, object]
    source_version: dict[str, object]
    chunk_id: str | None = None
    ticket_event_id: str | None = None
    fact_id: str | None = None
    message_id: str | None = None
    procedure_document_id: str | None = None
    procedure_check_key: str | None = None
    attachment_id: str | None = None
    usage_kind: Literal["METADATA", "CONTENT"] = "CONTENT"


def actor_ref(run: RunLease) -> ActorRef:
    """后台重核沿受理时不可登录会话引用，不保存Cookie。"""
    return ActorRef(
        owner_id=run.owner_id, session_ref=run.session_ref, auth_version=run.auth_version
    )


def source_query(run: RunLease, actor: ActorRef | None = None) -> SourceQuery:
    """环境与固定范围来自受理快照，不能让模型扩大客户或项目。"""
    snapshot = run.context_manifest
    result = SourceQuery(
        actor=actor or actor_ref(run),
        purpose="HISTORY",
        environment=Environment(
            **snapshot.support_context.model_dump(
                exclude_none=True, exclude={"schema_version", "field_sources"}
            )
            if snapshot.support_context
            else {}
        ),
    )
    for name in ("service_item_id", "device_id", "ticket_id"):
        value = getattr(snapshot, name)
        if value:
            setattr(result, name, value)
    return result


class EvidenceService:
    """所有交付面共用同一来源复核；撤权阻止正文、事实摘要和草稿泄漏。"""

    def __init__(self, engine: AsyncEngine, rpc: DomainRpc) -> None:
        """真实领域授权由RPC所有者提供，不把本地payload当权限。"""
        self.engine, self.rpc = engine, rpc

    async def list_items(self, run_id: str) -> list[Evidence]:
        """读取限长实际使用清单，不读取未使用候选。"""
        async with self.engine.connect() as db:
            rows = await queries.source_usages(db, {"id": run_id})
        return [
            Evidence(
                id=row["id"],
                number=row["source_number"],
                source_type=row["source_type"],
                title=row["title_snapshot"],
                excerpt=row["excerpt_snapshot"],
                locator=json.loads(row["locator_snapshot"]),
                source_version=json.loads(row["source_version"]),
                chunk_id=row["chunk_id"],
                ticket_event_id=row["ticket_event_id"],
                fact_id=row["fact_id"],
                message_id=row["message_id"],
                procedure_document_id=row.get("procedure_document_id"),
                procedure_check_key=row.get("procedure_check_key"),
                attachment_id=row.get("attachment_id"),
                usage_kind=row["usage_kind"],
            )
            for row in rows
        ]

    async def verify(self, run: RunLease, actor: ActorRef | None = None) -> list[Evidence]:
        """实际依赖取传递闭包，禁止仅凭本地factId绕过知识撤权。"""
        evidence = await self.list_items(run.id)
        await self.verify_items(run, evidence, actor or actor_ref(run), set())
        return evidence

    async def verify_items(
        self,
        run: RunLease,
        items: list[Evidence],
        actor: ActorRef,
        visited: set[str],
        ancestry: frozenset[str] = frozenset(),
        source_limit: int | None = None,
    ) -> None:
        """依赖数量有上限；跨会话引用和循环来源明确拒绝。"""
        if run.id in ancestry:
            raise BusinessError(CommonErrorCode.CONFLICT)
        ancestry = ancestry | {run.id}
        if source_limit is None:
            source_limit = run.pipeline_config.source_limit
        knowledge = []
        events = []
        attachments: dict[tuple[str, int], str] = {}
        for item in items:
            dependency = canonical_hash(
                {
                    "source_type": item.source_type,
                    "chunk_id": item.chunk_id,
                    "ticket_event_id": item.ticket_event_id,
                    "fact_id": item.fact_id,
                    "message_id": item.message_id,
                    "procedure_document_id": item.procedure_document_id,
                    "procedure_check_key": item.procedure_check_key,
                    "attachment_id": item.attachment_id,
                    "source_version": item.source_version,
                }
            )
            # 跨轮复制拥有新usage ID，但根身份及版本相同；只计一次授权依赖。
            # Run祖先仍独立检测，不能用来源去重跳过循环检查。
            if dependency in visited:
                continue
            visited.add(dependency)
            if len(visited) > source_limit:
                raise BusinessError(CommonErrorCode.CONFLICT)
            if item.source_type in {"CHUNK", "PROCEDURE_CHECK"}:
                source = item.source_version.get("knowledge_source")
                if not isinstance(source, dict):
                    raise BusinessError(CommonErrorCode.CONFLICT)
                knowledge.append((item.id, ParseDict(source, KnowledgeSource())))
            elif item.source_type == "ATTACHMENT":
                version = item.source_version.get("redaction_version")
                sha = item.source_version.get("sha256")
                if (
                    not item.attachment_id
                    or not run.context_manifest.ticket_id
                    or type(version) is not int
                    or version < 1
                    or not isinstance(sha, str)
                ):
                    raise BusinessError(CommonErrorCode.FORBIDDEN)
                key = (item.attachment_id, version)
                if key in attachments and attachments[key] != sha:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                attachments[key] = sha
            elif item.source_type == "TICKET_EVENT":
                if item.ticket_event_id is None:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                events.append(item.ticket_event_id)
            elif item.source_type == "FACT":
                async with self.engine.connect() as db:
                    fact = await queries.fact_dependency(db, {"id": item.fact_id})
                if (
                    fact is None
                    or fact["conversation_id"] != run.conversation_id
                    or fact["task_id"] != run.task_id
                ):
                    raise BusinessError(CommonErrorCode.FORBIDDEN)
                if fact["source_type"] == "TOOL_OBSERVATION":
                    if fact["source_run_id"] is None:
                        raise BusinessError(CommonErrorCode.FORBIDDEN)
                    async with self.engine.connect() as db:
                        parent = await read_run(db, fact["source_run_id"])
                    self.same_scope(run, parent)
                    await self.verify_items(
                        parent,
                        await self.list_items(parent.id),
                        actor,
                        visited,
                        ancestry,
                        source_limit,
                    )
            elif item.source_type == "MESSAGE":
                async with self.engine.connect() as db:
                    message = await queries.message_dependency(db, {"id": item.message_id})
                if (
                    message is None
                    or message["conversation_id"] != run.conversation_id
                    or message["task_id"] != run.task_id
                    or message["content_sha256"] != item.source_version.get("content_sha256")
                ):
                    raise BusinessError(CommonErrorCode.FORBIDDEN)
                if message["role"] == "ASSISTANT":
                    if message["source_run_id"] is None:
                        raise BusinessError(CommonErrorCode.FORBIDDEN)
                    async with self.engine.connect() as db:
                        parent = await read_run(db, message["source_run_id"])
                    self.same_scope(run, parent)
                    await self.verify_items(
                        parent,
                        await self.list_items(parent.id),
                        actor,
                        visited,
                        ancestry,
                        source_limit,
                    )
            else:
                raise BusinessError(CommonErrorCode.FORBIDDEN)
        try:
            for (attachment_id, version), sha in attachments.items():
                redacted = await self.rpc.read_redacted(
                    RedactedRequest(
                        actor=actor,
                        ticket_id=run.context_manifest.ticket_id,
                        attachment_id=attachment_id,
                        redaction_version=version,
                    )
                )
                if redacted.sha256 != sha:
                    raise BusinessError(CommonErrorCode.FORBIDDEN)
            for source_id, source in knowledge:
                query = source_query(run, actor)
                query.run_id = run.id
                query.source_usage_id = source_id
                await self.rpc.verify_sources(query, [source])
            if events or run.ticket_row_version is not None:
                batches = [
                    events[offset : offset + 20] for offset in range(0, len(events), 20)
                ] or [[]]
                for batch in batches:
                    await self.rpc.ticket.VerifyTicketAccess(
                        VerifyTicketRequest(
                            actor=actor,
                            ticket_id=run.context_manifest.ticket_id,
                            event_ids=batch,
                        ),
                        **self.rpc.options(),
                    )
        except grpc.aio.AioRpcError as error:
            failure = translate_failure(error)
            if failure.code == 5103:
                raise BusinessError(EvidenceErrorCode.SOURCE_WITHDRAWN) from None
            if failure.code in {1002, 1003}:
                raise BusinessError(CommonErrorCode.FORBIDDEN) from None
            raise failure from None

    @staticmethod
    def same_scope(run: RunLease, parent: RunLease) -> None:
        """历史依赖必须来自相同私有任务，不能凭内部ID跨任务或跨拥有者继承。"""
        if (
            parent.owner_id != run.owner_id
            or parent.conversation_id != run.conversation_id
            or parent.task_id != run.task_id
            or parent.id == run.id
        ):
            raise BusinessError(CommonErrorCode.FORBIDDEN)

    async def inherit(self, run: RunLease, parent_id: str) -> list[Evidence]:
        """继承权限追溯元信息；没有重新输入正文的旧来源不获得本轮操作依据资格。"""
        async with self.engine.connect() as db:
            parent = await read_run(db, parent_id)
        self.same_scope(run, parent)
        items = await self.verify(parent, actor_ref(run))
        inherited = []
        async with self.engine.begin() as db:
            for item in items:
                copied = await self.add(
                    db,
                    run,
                    source_type=item.source_type,
                    title=item.title,
                    excerpt=item.excerpt,
                    locator=item.locator,
                    source_version=item.source_version,
                    chunk_id=item.chunk_id,
                    ticket_event_id=item.ticket_event_id,
                    fact_id=item.fact_id,
                    message_id=item.message_id,
                    procedure_document_id=item.procedure_document_id,
                    procedure_check_key=item.procedure_check_key,
                    attachment_id=item.attachment_id,
                    usage_kind="METADATA",
                )
                inherited.append(copied)
        return inherited

    async def add(
        self,
        db: AsyncConnection,
        run: RunLease,
        *,
        source_type: str,
        title: str,
        excerpt: str,
        locator: dict[str, object],
        source_version: dict[str, object],
        chunk_id: str | None = None,
        ticket_event_id: str | None = None,
        fact_id: str | None = None,
        message_id: str | None = None,
        procedure_document_id: str | None = None,
        procedure_check_key: str | None = None,
        attachment_id: str | None = None,
        usage_kind: Literal["METADATA", "CONTENT"] = "CONTENT",
    ) -> Evidence:
        """只有真正裁剪进入上下文的摘录才登记；相同来源版本与摘录复用。"""
        await owned(db, run)
        identity = (
            chunk_id,
            ticket_event_id,
            fact_id,
            message_id,
            procedure_document_id,
            procedure_check_key,
            attachment_id,
        )
        rows = await queries.run_sources(db, {"id": run.id})
        version = {"schema_version": 1, "title": title, **source_version}
        for row in rows:
            if (
                identity
                == (
                    row["chunk_id"],
                    row["ticket_event_id"],
                    row["fact_id"],
                    row["message_id"],
                    row.get("procedure_document_id"),
                    row.get("procedure_check_key"),
                    row.get("attachment_id"),
                )
                and row["excerpt_snapshot"] == excerpt
                and json.loads(row["source_version"]) == version
                and row["usage_kind"] == usage_kind
            ):
                return Evidence(
                    row["id"],
                    row["source_number"],
                    source_type,
                    title,
                    excerpt,
                    locator,
                    version,
                    *identity,
                    usage_kind,
                )
        if len(rows) >= run.pipeline_config.source_limit:
            raise BusinessError(CommonErrorCode.CONFLICT)
        evidence = Evidence(
            new_id(),
            len(rows) + 1,
            source_type,
            title,
            excerpt,
            locator,
            version,
            *identity,
            usage_kind,
        )
        await queries.insert_source_usage(
            db,
            {
                "id": evidence.id,
                "run": run.id,
                "message": message_id,
                "attachment": attachment_id,
                "procedure": procedure_document_id,
                "check_key": procedure_check_key,
                "title": title,
                "number": evidence.number,
                "type": source_type,
                "chunk": chunk_id,
                "event": ticket_event_id,
                "fact": fact_id,
                "version": json_value(version),
                "locator": json_value(locator),
                "excerpt": excerpt,
                "usage": usage_kind,
            },
        )
        return evidence

    async def diagnostic_text(
        self, run: RunLease, attachment_id: str, version: int, offset: int
    ) -> list[Evidence]:
        """只读固定版本；按字符定位原样摘录，分页不得冒充完整附件或实测修复。"""
        if not run.context_manifest.ticket_id:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        value = await self.rpc.read_redacted(
            RedactedRequest(
                actor=actor_ref(run),
                ticket_id=run.context_manifest.ticket_id,
                attachment_id=attachment_id,
                redaction_version=version,
            )
        )
        if offset >= len(value.content):
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        end = min(offset + 4000, len(value.content))
        async with self.engine.begin() as db:
            item = await self.add(
                db,
                run,
                source_type="ATTACHMENT",
                title="已授权脱敏诊断文本",
                excerpt=value.content[offset:end],
                locator={
                    "schema_version": 1,
                    "kind": "ATTACHMENT",
                    "attachment_id": attachment_id,
                    "redaction_version": version,
                    "offset": offset,
                    "end_offset": end,
                },
                source_version={
                    "sha256": value.sha256,
                    "redaction_version": version,
                    "content_format": value.content_format,
                    "content_complete": offset == 0 and end == len(value.content),
                    "total_characters": len(value.content),
                    "next_offset": end if end < len(value.content) else None,
                },
                attachment_id=attachment_id,
            )
        return [item]

    async def check_feedback(self, db: AsyncConnection, run: RunLease, row: Mapping) -> Evidence:
        """完整人工反馈独立登记消息根来源，不因聊天窗口省略而只剩执行摘要。"""
        if not row["answer_message_id"] or not row["answer_sha256"] or not row["normalized_answer"]:
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        return await self.add(
            db,
            run,
            source_type="MESSAGE",
            title="检查的人工反馈",
            excerpt=json_value(
                {
                    "check_id": row["id"],
                    "check_key": row["check_key"],
                    "check_kind": row["check_kind"],
                    "requested_instruction": row["instruction"],
                    "instruction_scope": "该历史检查的提问内容，只解释本次反馈，不授予重新执行权限",
                    "result_fact_id": row["result_fact_id"],
                    "answer": json.loads(row["normalized_answer"]),
                    "answered_at": row["answered_at"].isoformat() if row["answered_at"] else None,
                    "context_version": row["context_version"],
                    "historical_environment": row["context_version"]
                    != run.diagnosis_context_version,
                }
            ),
            message_id=row["answer_message_id"],
            locator={
                "schema_version": 1,
                "kind": "MESSAGE",
                "message_id": row["answer_message_id"],
            },
            source_version={
                "content_sha256": row["answer_sha256"],
                "message_role": "USER",
                "check_id": row["id"],
                "row_version": row["row_version"],
                "proposed_run_id": row["proposed_run_id"],
            },
        )

    async def procedure(self, run: RunLease, document_id: str, check_key: str) -> list[Evidence]:
        """逐次授权固定修订，只将选定检查及全局安全约束作为实际来源输入。"""
        response = await self.rpc.procedures.ReadProcedure(
            ProcedureRequest(context=source_query(run), document_id=document_id),
            **self.rpc.options(),
        )
        raw = json.loads(response.definition_json)
        definition = ProcedureDefinition.model_validate(raw)
        if canonical_hash(raw) != response.definition_sha256:
            raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
        check = next((item for item in definition.checks if item.check_key == check_key), None)
        if check is None:
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        snapshot = ProcedureSnapshot(
            schema_version=1,
            definition_sha256=response.definition_sha256,
            applicability=definition.applicability,
            required_fields=definition.required_fields,
            stop_conditions=definition.stop_conditions,
            handover_conditions=definition.handover_conditions,
            check=check,
        ).model_dump(mode="json")
        async with self.engine.begin() as db:
            item = await self.add(
                db,
                run,
                source_type="PROCEDURE_CHECK",
                title=response.source.title,
                excerpt=json_value(snapshot),
                locator={"schema_version": 1, **check.source_locator.model_dump(exclude_none=True)},
                source_version={
                    "content_complete": True,
                    "procedure": snapshot,
                    "knowledge_source": MessageToDict(
                        response.source, preserving_proto_field_name=True
                    ),
                },
                procedure_document_id=document_id,
                procedure_check_key=check_key,
            )
        return [item]

    async def knowledge(
        self,
        run: RunLease,
        ids: list[str],
        allowed: list[KnowledgeSource],
        *,
        complete: bool = False,
        expand_section: bool = False,
        offset: int = 0,
    ) -> list[Evidence]:
        """预览与正文页明确区分；正文按Token分页，原字符区间可验证完整覆盖。"""
        if not ids:
            return []
        allowed_jobs = {source.index_job_id: source for source in allowed}
        result: list[Evidence] = []
        preview_tokens = 0
        async with self.engine.begin() as db:
            for identifier in ids:
                row = await queries.readable_chunk(db, {"id": identifier})
                if row is not None and expand_section and row["parent_chunk_id"]:
                    if offset:
                        # 叶片与父章节的字符坐标不同，不能沿用叶片预览或正文游标。
                        raise BusinessError(CommonErrorCode.INVALID_REQUEST)
                    row = await queries.readable_chunk(db, {"id": row["parent_chunk_id"]})
                if (
                    row is None
                    or row["index_job_id"] not in allowed_jobs
                    or row["status"] != "SUCCEEDED"
                    or row["last_command"] != "SUBMIT"
                ):
                    raise BusinessError(CommonErrorCode.CONFLICT)
                source = allowed_jobs[row["index_job_id"]]
                if source.document_id != row["document_id"]:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                content = row["content"]
                continuation: dict[str, object] = {}
                if row.get("node_kind") == "SECTION":
                    parts = await queries.adjacent_sections(db, row["id"])
                    position = next(i for i, part in enumerate(parts) if part["id"] == row["id"])
                    continuation = {
                        "section_part": position + 1,
                        "section_parts": len(parts),
                        "previous_section_id": parts[position - 1]["id"] if position else None,
                        "next_section_id": parts[position + 1]["id"]
                        if position + 1 < len(parts)
                        else None,
                    }
                if len(content.encode("utf-8")) > 48 * 1024:
                    raise BusinessError(CommonErrorCode.PAYLOAD_TOO_LARGE)
                if not 0 <= offset < len(content) or (offset and not complete):
                    raise BusinessError(CommonErrorCode.INVALID_REQUEST)
                if not complete:
                    candidate_tokens = (
                        estimate_text_tokens(
                            json.dumps(
                                {
                                    "title": source.title,
                                    "content": content[:400],
                                    "locator": json.loads(row["source_locations"]),
                                    "chunkId": row["id"],
                                    "documentId": source.document_id,
                                },
                                ensure_ascii=False,
                            )
                        )
                        + 200
                    )
                    if result and preview_tokens + candidate_tokens > 2000:
                        break
                    preview_tokens += candidate_tokens
                end = min(len(content), 400)
                if complete:
                    low, high = offset + 1, len(content)
                    while low < high:
                        middle = (low + high + 1) // 2
                        if estimate_text_tokens(content[offset:middle]) <= 1200:
                            low = middle
                        else:
                            high = middle - 1
                    end = low
                continuation.update(
                    {
                        "content_offset": offset,
                        "content_end": end,
                        "content_length": len(content),
                        "next_offset": end if complete and end < len(content) else None,
                    }
                )
                result.append(
                    await self.add(
                        db,
                        run,
                        source_type="CHUNK",
                        title=source.title,
                        excerpt=content[offset:end],
                        locator=json.loads(row["source_locations"]),
                        source_version={
                            **continuation,
                            "node_kind": row.get("node_kind", "LEAF"),
                            "content_sha256": row.get("content_sha256"),
                            "content_complete": complete and offset == 0 and end == len(content),
                            "knowledge_source": MessageToDict(
                                source, preserving_proto_field_name=True
                            ),
                        },
                        chunk_id=row["id"],
                        usage_kind="CONTENT" if complete else "METADATA",
                    )
                )
        return result
