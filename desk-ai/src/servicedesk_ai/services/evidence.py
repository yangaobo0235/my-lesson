"""实际上下文来源、传递依赖及交付前复核，候选集不自动成为证据。作者：yangaobo。"""

from __future__ import annotations

import json
from dataclasses import dataclass

import grpc
from google.protobuf.json_format import MessageToDict, ParseDict
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine

from desk_contracts.common.v1.common_pb2 import ActorRef
from desk_contracts.knowledge.v1.source_pb2 import KnowledgeSource, SourceQuery
from desk_contracts.ticket.v1.support_pb2 import Environment, VerifyTicketRequest
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc
from servicedesk_ai.infrastructure.identity import translate_failure
from servicedesk_ai.repositories.diagnosis import Generation, json_value, new_id, owned


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


def actor_ref(run: Generation) -> ActorRef:
    """后台重核沿受理时不可登录会话引用，不保存Cookie。"""
    return ActorRef(
        owner_id=run.owner_id, session_ref=run.session_ref, auth_version=run.auth_version
    )


def source_query(run: Generation, actor: ActorRef | None = None) -> SourceQuery:
    """环境与固定范围来自受理快照，不能让模型扩大客户或项目。"""
    snapshot = run.context_snapshot
    environment = Environment()
    for name, value in snapshot.get("environment", {}).items():
        if value is not None and name in Environment.DESCRIPTOR.fields_by_name:
            setattr(environment, name, value)
    result = SourceQuery(actor=actor or actor_ref(run), purpose="HISTORY", environment=environment)
    for name in ("service_item_id", "device_id", "ticket_id"):
        if snapshot.get(name):
            setattr(result, name, snapshot[name])
    return result


class EvidenceService:
    """所有交付面共用同一来源复核；撤权阻止正文、事实摘要和草稿泄漏。"""

    def __init__(self, engine: AsyncEngine, rpc: DomainRpc) -> None:
        """真实领域授权由RPC所有者提供，不把本地payload当权限。"""
        self.engine, self.rpc = engine, rpc

    async def list(self, run_id: str) -> list[Evidence]:
        """读取限长实际使用清单，不读取未使用候选。"""
        async with self.engine.connect() as db:
            rows = (
                (
                    await db.execute(
                        text("""
                SELECT id,source_number,source_type,chunk_id,ticket_event_id,
                fact_id,source_version,locator_snapshot,excerpt_snapshot FROM ai_source_usage
                WHERE generation_id=:id ORDER BY source_number
            """),
                        {"id": run_id},
                    )
                )
                .mappings()
                .all()
            )
        return [
            Evidence(
                id=row["id"],
                number=row["source_number"],
                source_type=row["source_type"],
                title=json.loads(row["source_version"]).get("title", "来源"),
                excerpt=row["excerpt_snapshot"],
                locator=json.loads(row["locator_snapshot"]),
                source_version=json.loads(row["source_version"]),
                chunk_id=row["chunk_id"],
                ticket_event_id=row["ticket_event_id"],
                fact_id=row["fact_id"],
            )
            for row in rows
        ]

    async def verify(self, run: Generation, actor: ActorRef | None = None) -> list[Evidence]:
        """实际依赖取传递闭包，禁止仅凭本地factId绕过知识撤权。"""
        evidence = await self.list(run.id)
        await self.verify_items(run, evidence, actor or actor_ref(run), set())
        return evidence

    async def verify_items(
        self, run: Generation, items: list[Evidence], actor: ActorRef, visited: set[str]
    ) -> None:
        """依赖数量有上限；跨会话引用和循环来源明确拒绝。"""
        knowledge = []
        events = []
        for item in items:
            if item.id in visited:
                continue
            visited.add(item.id)
            if len(visited) > 64:
                raise BusinessError(CommonErrorCode.CONFLICT)
            if item.source_type == "KNOWLEDGE_CHUNK":
                knowledge.append(
                    ParseDict(item.source_version["knowledge_source"], KnowledgeSource())
                )
            elif item.source_type == "TICKET_EVENT":
                events.append(item.ticket_event_id)
            elif item.source_type == "DIAGNOSTIC_FACT":
                async with self.engine.connect() as db:
                    fact = (
                        (
                            await db.execute(
                                text("""
                SELECT conversation_id,source_type,source_generation_id
                FROM ai_diagnostic_fact WHERE id=:id
            """),
                                {"id": item.fact_id},
                            )
                        )
                        .mappings()
                        .first()
                    )
                if fact is None or fact["conversation_id"] != run.conversation_id:
                    raise BusinessError(CommonErrorCode.FORBIDDEN)
                if fact["source_type"] in {"ASSISTANT_PROPOSAL", "TOOL_OBSERVATION"}:
                    from servicedesk_ai.repositories.diagnosis import generation

                    async with self.engine.connect() as db:
                        parent = await generation(db, fact["source_generation_id"])
                    await self.verify_items(parent, await self.list(parent.id), actor, visited)
        try:
            if knowledge:
                await self.rpc.verify_sources(source_query(run, actor), knowledge)
            if events or run.ticket_version is not None:
                batches = [
                    events[offset : offset + 20] for offset in range(0, len(events), 20)
                ] or [[]]
                for batch in batches:
                    await self.rpc.ticket.VerifyTicketContext(
                        VerifyTicketRequest(
                            actor=actor,
                            ticket_id=run.context_snapshot["ticket_id"],
                            ticket_version=run.ticket_version,
                            event_ids=batch,
                        ),
                        **self.rpc.options(),
                    )
        except grpc.aio.AioRpcError as error:
            failure = translate_failure(error)
            if error.code() in {
                grpc.StatusCode.PERMISSION_DENIED,
                grpc.StatusCode.NOT_FOUND,
                grpc.StatusCode.FAILED_PRECONDITION,
            }:
                raise BusinessError(CommonErrorCode.FORBIDDEN) from None
            raise failure from None

    async def add(
        self,
        db: AsyncConnection,
        run: Generation,
        *,
        source_type: str,
        title: str,
        excerpt: str,
        locator: dict[str, object],
        source_version: dict[str, object],
        chunk_id: str | None = None,
        ticket_event_id: str | None = None,
        fact_id: str | None = None,
    ) -> Evidence:
        """只有真正裁剪进入上下文的摘录才登记；相同来源版本与摘录复用。"""
        await owned(db, run)
        identity = (chunk_id, ticket_event_id, fact_id)
        rows = (
            (
                await db.execute(
                    text("""
                SELECT id,source_number,chunk_id,ticket_event_id,fact_id,
                excerpt_snapshot,source_version FROM ai_source_usage WHERE generation_id=:id
                ORDER BY source_number
            """),
                    {"id": run.id},
                )
            )
            .mappings()
            .all()
        )
        version = {"schema_version": 1, "title": title, **source_version}
        for row in rows:
            if (
                identity == (row["chunk_id"], row["ticket_event_id"], row["fact_id"])
                and row["excerpt_snapshot"] == excerpt
                and json.loads(row["source_version"]) == version
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
                )
        if len(rows) >= 64:
            raise BusinessError(CommonErrorCode.CONFLICT)
        evidence = Evidence(
            new_id(), len(rows) + 1, source_type, title, excerpt, locator, version, *identity
        )
        await db.execute(
            text("""
                INSERT INTO ai_source_usage
                (id,generation_id,conversation_id,source_number,source_type,chunk_id,ticket_event_id,fact_id,
                source_version,locator_snapshot,excerpt_snapshot)
                VALUES
                (:id,:run,:conversation,:number,:type,:chunk,:event,:fact,:version,:locator,:excerpt)
            """),
            {
                "id": evidence.id,
                "run": run.id,
                "conversation": run.conversation_id,
                "number": evidence.number,
                "type": source_type,
                "chunk": chunk_id,
                "event": ticket_event_id,
                "fact": fact_id,
                "version": json_value(version),
                "locator": json_value(locator),
                "excerpt": excerpt,
            },
        )
        manifest = [row["id"] for row in rows] + [evidence.id]
        await db.execute(
            text("""
                UPDATE ai_generation SET context_snapshot=JSON_SET(context_snapshot,
                '$.source_usage_ids',CAST(:manifest AS JSON)) WHERE id=:id
            """),
            {"id": run.id, "manifest": json_value(manifest)},
        )
        return evidence

    async def knowledge(
        self, run: Generation, ids: list[str], allowed: list[KnowledgeSource]
    ) -> list[Evidence]:
        """融合后回查MySQL任务与正文，公开标题及版本取knowledge事实。"""
        if not ids:
            return []
        allowed_jobs = {source.index_job_id: source for source in allowed}
        result = []
        async with self.engine.begin() as db:
            for identifier in ids:
                row = (
                    (
                        await db.execute(
                            text("""
                SELECT c.id,c.index_job_id,c.document_id,c.content,c.source_locations,
                j.status,j.last_command FROM ai_chunk c JOIN ai_index_job j ON
                j.id=c.index_job_id
                WHERE c.id=:id
            """),
                            {"id": identifier},
                        )
                    )
                    .mappings()
                    .first()
                )
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
                result.append(
                    await self.add(
                        db,
                        run,
                        source_type="KNOWLEDGE_CHUNK",
                        title=source.title,
                        excerpt=row["content"][:400],
                        locator=json.loads(row["source_locations"]),
                        source_version={
                            "knowledge_source": MessageToDict(
                                source, preserving_proto_field_name=True
                            )
                        },
                        chunk_id=identifier,
                    )
                )
        return result
