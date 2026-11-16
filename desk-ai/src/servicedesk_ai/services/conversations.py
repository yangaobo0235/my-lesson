"""私有会话、版本化事实与持久生成受理，不在Web请求内调用付费模型。作者：yangaobo。"""

from __future__ import annotations

import json
from datetime import UTC, datetime, timedelta, timezone
from inspect import isawaitable
from typing import overload

import grpc
from google.protobuf.json_format import MessageToDict
from redis.asyncio import Redis
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncEngine

from desk_contracts.ticket.v1.support_pb2 import (
    ContextRequest,
    Environment,
    SupportContext,
    TicketRequest,
)
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.generation import GenerationErrorCode
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc
from servicedesk_ai.infrastructure.identity import translate_failure
from servicedesk_ai.infrastructure.tracing import snapshot as trace_snapshot
from servicedesk_ai.repositories import conversation_queries as queries
from servicedesk_ai.repositories import diagnosis as store
from servicedesk_ai.schemas.diagnosis import (
    ConversationChange,
    ConversationCreate,
    FactChange,
    GenerationCreate,
    SupportEnvironment,
)
from servicedesk_ai.schemas.diagnosis_views import (
    CallsView,
    CitationView,
    ConversationView,
    FactView,
    GenerationView,
    ModelCallView,
    PageView,
    ToolCallView,
)
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.services.evidence import EvidenceService


@overload
def instant(value: datetime) -> datetime: ...


@overload
def instant(value: None) -> None: ...


def instant(value: datetime | None) -> datetime | None:
    """MySQL UTC无时区列在公开边界恢复UTC，不按宿主本地时区解释。"""
    return value.replace(tzinfo=UTC) if value is not None else None


def environment(value: dict[str, object]) -> SupportEnvironment:
    """版本字段留持久合同，其余仅允许具名环境字段。"""
    return SupportEnvironment.model_validate(
        {key: item for key, item in value.items() if key != "schema_version"}
    )


async def async_eval(redis: Redis, script: str, count: int, *args: str | int) -> object:
    """只等待所属异步Redis资源，防止错误同步连接进入事件循环。"""
    result = redis.eval(script, count, *args)
    if not isawaitable(result):
        raise TypeError("需要异步Redis资源")
    return await result


class ConversationService:
    """当前身份及资源权限每次核验，会话所有权不随工单转派共享。"""

    def __init__(
        self,
        engine: AsyncEngine,
        redis: Redis,
        rpc: DomainRpc,
        settings: Settings,
        evidence: EvidenceService,
    ) -> None:
        """资源均由所属进程创建，服务不保存单个请求可变状态。"""
        self.engine, self.redis, self.rpc, self.settings, self.evidence = (
            engine,
            redis,
            rpc,
            settings,
            evidence,
        )

    def owner(self, actor: VerifiedIdentity, value: store.Conversation) -> None:
        """管理员或其他技术人员拥有工单权，也不能读取他人的私有会话。"""
        if actor.actor.owner_id != value.owner_id:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        if value.mode == "CUSTOMER_SELF_SERVICE":
            if "CUSTOMER_USER" not in actor.roles or actor.organization_id != value.customer_id:
                raise BusinessError(CommonErrorCode.FORBIDDEN)
        elif "TECHNICIAN" not in actor.roles:
            raise BusinessError(CommonErrorCode.FORBIDDEN)

    async def context(
        self,
        actor: VerifiedIdentity,
        *,
        mode: str,
        service_item_id: str | None,
        device_id: str | None,
        ticket_id: str | None,
        reported: SupportEnvironment,
        purpose: str = "HISTORY",
    ) -> SupportContext:
        """跨语言合同只提供原ActorRef，范围和当前工单环境由ticket决定。"""
        try:
            if mode == "TECHNICIAN_ASSIST":
                if "TECHNICIAN" not in actor.roles:
                    raise BusinessError(CommonErrorCode.FORBIDDEN)
                return await self.rpc.ticket.ReadTicketContext(
                    TicketRequest(actor=actor.actor, ticket_id=ticket_id), **self.rpc.options()
                )
            if "CUSTOMER_USER" not in actor.roles:
                raise BusinessError(CommonErrorCode.FORBIDDEN)
            request = ContextRequest(
                actor=actor.actor,
                purpose=purpose,
                service_item_id=service_item_id,
                environment=Environment(**reported.model_dump(exclude_none=True)),
            )
            if device_id:
                request.device_id = device_id
            return await self.rpc.ticket.CheckContext(request, **self.rpc.options())
        except grpc.aio.AioRpcError as error:
            raise translate_failure(error) from None

    async def authorized(self, actor: VerifiedIdentity, identifier: str) -> store.Conversation:
        """客户读取保留本人问题，技术读取还要当前工单范围；快照不授予权限。"""
        async with self.engine.connect() as db:
            value = await store.conversation(db, identifier)
        self.owner(actor, value)
        if value.mode == "TECHNICIAN_ASSIST":
            context = await self.context(
                actor,
                mode=value.mode,
                service_item_id=None,
                device_id=None,
                ticket_id=value.ticket_id,
                reported=environment(value.support_context),
            )
            if context.customer_id != value.customer_id:
                raise BusinessError(CommonErrorCode.FORBIDDEN)
        return value

    def view(self, value: store.Conversation) -> ConversationView:
        """所有版本公开为字符串，UTC时刻带偏移。"""
        return ConversationView(
            id=value.id,
            title=value.title,
            mode=value.mode,
            customer_id=value.customer_id,
            service_item_id=value.service_item_id,
            ticket_id=value.ticket_id,
            device_id=value.device_id,
            support_context=environment(value.support_context),
            state_version=str(value.state_version),
            row_version=str(value.row_version),
            created_at=instant(value.created_at),
            updated_at=instant(value.updated_at),
        )

    async def create(self, actor: VerifiedIdentity, form: ConversationCreate) -> ConversationView:
        """绑定对象核验后短事务创建，技术环境不接受浏览器覆盖。"""
        context = await self.context(
            actor,
            mode=form.mode,
            service_item_id=form.service_item_id,
            device_id=form.device_id,
            ticket_id=form.ticket_id,
            reported=form.support_context or SupportEnvironment(),
            purpose="NEW_SELF_SERVICE",
        )
        identifier = store.new_id()
        snapshot = {
            "schema_version": 1,
            **MessageToDict(context.environment, preserving_proto_field_name=True),
        }
        snapshot = SupportEnvironment.model_validate(
            {k: v for k, v in snapshot.items() if k != "schema_version"}
        ).model_dump()
        async with self.engine.begin() as db:
            await queries.insert_conversation(
                db,
                {
                    "id": identifier,
                    "owner": actor.actor.owner_id,
                    "customer": context.customer_id,
                    "mode": form.mode,
                    "ticket": form.ticket_id,
                    "service": context.service_item_id or None,
                    "device": context.device_id or None,
                    "model": context.product_model_id or None,
                    "context": store.json_value({"schema_version": 1, **snapshot}),
                    "title": form.title.strip(),
                },
            )
            value = await store.conversation(db, identifier)
        return self.view(value)

    async def get(self, actor: VerifiedIdentity, identifier: str) -> ConversationView:
        """读取当前拥有者会话。"""
        return self.view(await self.authorized(actor, identifier))

    async def list(
        self,
        actor: VerifiedIdentity,
        page: int,
        size: int,
        mode: str | None = None,
        ticket_id: str | None = None,
    ) -> PageView[ConversationView]:
        """按本人数据库分页，不读取全库；技术记录逐项当前授权。"""
        parameters = {"owner": actor.actor.owner_id, "mode": mode, "ticket": ticket_id}
        async with self.engine.connect() as db:
            count = await queries.count_conversations(db, parameters)
            rows = await queries.page_conversation_ids(
                db, {**parameters, "limit": size, "offset": (page - 1) * size}
            )
        values = []
        for identifier in rows:
            try:
                values.append(self.view(await self.authorized(actor, identifier)))
            except BusinessError as error:
                if error.error not in {CommonErrorCode.NOT_FOUND, CommonErrorCode.FORBIDDEN}:
                    raise
        return PageView(records=values, total=str(count), page_num=page, page_size=size)

    async def change(
        self,
        actor: VerifiedIdentity,
        identifier: str,
        form: ConversationChange,
        delete: bool = False,
    ) -> ConversationView | None:
        """删除先撤销执行权，事实和正式工单历史不物理删除。"""
        await self.authorized(actor, identifier)
        if not delete and (form.title is None or not form.title.strip()):
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        async with self.engine.begin() as db:
            value = await store.conversation(db, identifier, lock=True)
            self.owner(actor, value)
            if value.row_version != int(form.row_version):
                raise BusinessError(CommonErrorCode.CONFLICT)
            if delete:
                active = await queries.lock_active_generation_ids(db, {"id": identifier})
                for run_id in active:
                    await store.stop(
                        db, await store.generation(db, run_id), "CANCELLED", "CONVERSATION_DELETED"
                    )
                await queries.mark_conversation_deleted(db, {"id": identifier})
                return None
            await queries.rename_conversation(
                db, {"id": identifier, "title": (form.title or "").strip()}
            )
            return self.view(await store.conversation(db, identifier))

    async def facts(
        self, actor: VerifiedIdentity, identifier: str, page: int, size: int
    ) -> PageView[FactView]:
        """更正记录追加保留；来源撤回隐藏相关提议，不隐藏本人原始反馈。"""
        value = await self.authorized(actor, identifier)
        async with self.engine.connect() as db:
            count = await queries.count_facts(db, {"id": identifier})
            rows = await queries.page_facts(
                db, {"id": identifier, "limit": size, "offset": (page - 1) * size}
            )
        result = []
        verified = {}
        for row in rows:
            hidden = False
            if row["source_type"] in {"ASSISTANT_PROPOSAL", "TOOL_OBSERVATION"}:
                parent = row["source_generation_id"]
                if parent not in verified:
                    verified[parent] = (await self.get_generation(actor, parent)).sources_hidden
                hidden = verified[parent]
            locator = json.loads(row["source_locator"])
            old = locator.get("environment", {})
            current = {k: v for k, v in value.support_context.items() if k != "schema_version"}
            historical = any(
                old.get(k) not in (None, "UNKNOWN", "")
                and v not in (None, "UNKNOWN", "")
                and old[k] != v
                for k, v in current.items()
            )
            result.append(
                FactView(
                    id=row["id"],
                    state_version=str(row["state_version"]),
                    fact_kind=row["fact_kind"],
                    operation=row["operation"],
                    active=bool(row["active"]),
                    statement=None if hidden else row["statement"],
                    source_type=row["source_type"],
                    supersedes_fact_id=row["supersedes_fact_id"],
                    source_generation_id=row["source_generation_id"],
                    sources_hidden=hidden,
                    historical_environment=historical,
                    created_at=instant(row["created_at"]),
                )
            )
        return PageView(records=result, total=str(count), page_num=page, page_size=size)

    async def fact_change(
        self, actor: VerifiedIdentity, identifier: str, form: FactChange
    ) -> ConversationView:
        """会话锁、原键和事实版本共同保护更正与撤回，客户端不能设置可信来源。"""
        await self.authorized(actor, identifier)
        request_hash = canonical_hash(form.model_dump(mode="json"))
        async with self.engine.begin() as db:
            value = await store.conversation(db, identifier, lock=True)
            self.owner(actor, value)
            old = await queries.find_fact_request(
                db, {"id": identifier, "key": form.client_action_id}
            )
            if old:
                if old[0] != request_hash:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                return self.view(value)
            await store.require_idle(db, identifier)
            if value.state_version != int(form.state_version):
                raise BusinessError(CommonErrorCode.CONFLICT)
            if form.action == "SET_CONTEXT" and value.mode != "CUSTOMER_SELF_SERVICE":
                raise BusinessError(CommonErrorCode.FORBIDDEN)
            if form.fact_id:
                predecessor = await queries.lock_current_assertion(
                    db, {"fact": form.fact_id, "id": identifier}
                )
                if predecessor is None:
                    raise BusinessError(CommonErrorCode.CONFLICT)
            snapshot = value.support_context
            if form.action == "SET_CONTEXT":
                if form.support_context is None:
                    raise BusinessError(CommonErrorCode.INVALID_REQUEST)
                snapshot = {"schema_version": 1, **form.support_context.model_dump()}
            statement = form.statement or "本人更新了问题环境，旧环境检查需复核"
            await queries.insert_user_fact(
                db,
                {
                    "fact": store.new_id(),
                    "id": identifier,
                    "version": value.state_version + 1,
                    "kind": form.fact_kind,
                    "operation": "RETRACT" if form.action == "RETRACT" else "ASSERT",
                    "statement": statement,
                    "source": "USER_REPORT"
                    if value.mode == "CUSTOMER_SELF_SERVICE"
                    else "STAFF_RECORD",
                    "actor": actor.actor.owner_id,
                    "locator": store.json_value(
                        {"schema_version": 1, "environment": snapshot, "action": form.action}
                    ),
                    "predecessor": form.fact_id,
                    "key": form.client_action_id,
                    "hash": request_hash,
                },
            )
            await queries.update_environment(
                db, {"id": identifier, "context": store.json_value(snapshot)}
            )
            return self.view(await store.conversation(db, identifier))

    async def generate(
        self, actor: VerifiedIdentity, identifier: str, form: GenerationCreate
    ) -> GenerationView:
        """先查拥有者原键；新受理同事务写输入事实和排队运行，额度失败不调用模型。"""
        request_hash = canonical_hash({"conversation_id": identifier, **form.model_dump()})
        async with self.engine.connect() as db:
            old = await store.generation_receipt(db, actor.actor.owner_id, form.client_request_id)
        if old:
            if old.request_hash != request_hash:
                raise BusinessError(CommonErrorCode.CONFLICT)
            return await self.get_generation(actor, old.id)
        value = await self.authorized(actor, identifier)
        context = await self.context(
            actor,
            mode=value.mode,
            service_item_id=value.service_item_id,
            device_id=value.device_id,
            ticket_id=value.ticket_id,
            reported=environment(value.support_context),
        )
        queue_key = (
            self.settings.redis.key_prefix
            + "daily:"
            + datetime.now(timezone(timedelta(hours=8))).strftime("%Y%m%d")
            + ":"
            + actor.actor.owner_id
        )
        accepted = await async_eval(
            self.redis,
            "local n=tonumber(redis.call('GET',KEYS[1]) or '0'); "
            "if n>=tonumber(ARGV[1]) then return 0 end; "
            "redis.call('INCR',KEYS[1]); redis.call('EXPIRE',KEYS[1],172800); return 1",
            1,
            queue_key,
            self.settings.redis.daily_limit,
        )
        if not accepted:
            raise BusinessError(GenerationErrorCode.DAILY_GENERATION_LIMIT)
        created = False
        try:
            async with self.engine.begin() as db:
                value = await store.conversation(db, identifier, lock=True)
                self.owner(actor, value)
                old = await store.generation_receipt(
                    db, actor.actor.owner_id, form.client_request_id
                )
                if old:
                    if old.request_hash != request_hash:
                        raise BusinessError(CommonErrorCode.CONFLICT)
                    run_id = old.id
                else:
                    if value.state_version != int(form.state_version):
                        raise BusinessError(CommonErrorCode.CONFLICT)
                    await store.require_idle(db, identifier)
                    queued = await queries.queued_generation_count(db)
                    if queued >= 100:
                        raise BusinessError(GenerationErrorCode.GENERATION_QUEUE_FULL)
                    round_number = await queries.next_round_number(db, {"id": identifier})
                    snapshot: dict[str, object] = {
                        "schema_version": 1,
                        "mode": value.mode,
                        "customer_id": value.customer_id,
                        "service_item_id": context.service_item_id or None,
                        "device_id": context.device_id or None,
                        "product_model_id": context.product_model_id or None,
                        "ticket_id": value.ticket_id,
                        "environment": SupportEnvironment.model_validate(
                            MessageToDict(context.environment, preserving_proto_field_name=True)
                        ).model_dump(),
                        "source_usage_ids": [],
                        "ticket_title": context.title,
                        "ticket_symptom": context.symptom,
                    }
                    run_id = store.new_id()
                    config = {
                        "schema_version": 1,
                        **self.settings.execution.model_dump(),
                        "prompt_version": "diagnosis-v1",
                        "tool_version": "readonly-v1",
                        "model": self.settings.model.chat_model,
                    }
                    await queries.insert_generation(
                        db,
                        {
                            "id": run_id,
                            "conversation": identifier,
                            "owner": actor.actor.owner_id,
                            "round": round_number,
                            "version": value.state_version + 1,
                            "ticket_version": context.ticket_version
                            if context.HasField("ticket_version")
                            else None,
                            "question": form.question.strip(),
                            "key": form.client_request_id,
                            "hash": request_hash,
                            "session": actor.actor.session_ref,
                            "auth": actor.actor.auth_version,
                            "pipeline": store.json_value(config),
                            "context": store.json_value(snapshot),
                            "trace": store.json_value(trace_snapshot())
                            if trace_snapshot()
                            else None,
                        },
                    )
                    await queries.insert_question_fact(
                        db,
                        {
                            "id": store.new_id(),
                            "conversation": identifier,
                            "version": value.state_version + 1,
                            "question": form.question.strip(),
                            "source": "USER_REPORT"
                            if value.mode == "CUSTOMER_SELF_SERVICE"
                            else "STAFF_RECORD",
                            "run": run_id,
                            "actor": actor.actor.owner_id,
                            "locator": store.json_value(
                                {
                                    "schema_version": 1,
                                    "environment": snapshot["environment"],
                                    "input": "QUESTION",
                                }
                            ),
                        },
                    )
                    await queries.advance_environment(
                        db,
                        {
                            "id": identifier,
                            "context": store.json_value(
                                {
                                    "schema_version": 1,
                                    **MessageToDict(
                                        context.environment, preserving_proto_field_name=True
                                    ),
                                }
                            ),
                        },
                    )
                    created = True
        except IntegrityError:
            async with self.engine.connect() as db:
                old = await store.generation_receipt(
                    db, actor.actor.owner_id, form.client_request_id
                )
            if old is None or old.request_hash != request_hash:
                raise BusinessError(CommonErrorCode.CONFLICT) from None
            run_id = old.id
            created = False
        finally:
            if not created:
                await async_eval(
                    self.redis,
                    "local n=tonumber(redis.call('GET',KEYS[1]) or '0'); "
                    "if n>0 then return redis.call('DECR',KEYS[1]) end; return 0",
                    1,
                    queue_key,
                )
        return await self.get_generation(actor, run_id)

    async def get_generation(self, actor: VerifiedIdentity, identifier: str) -> GenerationView:
        """全部实际来源复核失败则隐藏输出，不把故障误报成问题已解决。"""
        async with self.engine.connect() as db:
            run = await store.generation(db, identifier)
        await self.authorized(actor, run.conversation_id)
        hidden = False
        try:
            await self.evidence.verify(run, actor.actor)
        except BusinessError as error:
            if error.error != CommonErrorCode.FORBIDDEN:
                raise
            hidden = True
        async with self.engine.connect() as db:
            rows = await queries.generation_citations(db, {"id": identifier})
        return GenerationView(
            id=run.id,
            conversation_id=run.conversation_id,
            question=run.question_text,
            status=run.status,
            answer_type=None if hidden else run.answer_type,
            answer=None if hidden else run.answer_text,
            structured_result=None if hidden else run.structured_result,
            input_state_version=str(run.input_state_version),
            output_state_version=None
            if run.output_state_version is None
            else str(run.output_state_version),
            ticket_version=None if run.ticket_version is None else str(run.ticket_version),
            sources_hidden=hidden,
            citations=[]
            if hidden
            else [
                CitationView(
                    number=row["citation_number"],
                    source_usage_id=row["source_usage_id"],
                    title=row["title_snapshot"],
                    locator=json.loads(row["locator_snapshot"]),
                    excerpt=row["evidence_excerpt"],
                    document_id=json.loads(row["source_version"])
                    .get("knowledge_source", {})
                    .get("document_id"),
                )
                for row in rows
            ],
            input_tokens=None if run.input_tokens is None else str(run.input_tokens),
            output_tokens=None if run.output_tokens is None else str(run.output_tokens),
            error_code=run.error_code,
            created_at=instant(run.created_at),
            finished_at=instant(run.finished_at),
        )

    async def by_request(self, actor: VerifiedIdentity, key: str) -> GenerationView:
        """仅拥有者原请求键可查，不查其他账号回执。"""
        async with self.engine.connect() as db:
            run = await store.generation_receipt(db, actor.actor.owner_id, key)
        if run is None:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        return await self.get_generation(actor, run.id)

    async def turns(
        self, actor: VerifiedIdentity, identifier: str, page: int, size: int
    ) -> PageView[GenerationView]:
        """历史分页逐轮复核实际来源，断线只查询原运行，不重新生成。"""
        await self.authorized(actor, identifier)
        async with self.engine.connect() as db:
            count = await queries.count_turns(db, {"id": identifier})
            ids = await queries.page_generation_ids(
                db, {"id": identifier, "limit": size, "offset": (page - 1) * size}
            )
        return PageView(
            records=[await self.get_generation(actor, item) for item in ids],
            total=str(count),
            page_num=page,
            page_size=size,
        )

    async def cancel(self, actor: VerifiedIdentity, identifier: str) -> GenerationView:
        """先提交终态和撤销执行权，断开订阅本身不调用此动作。"""
        async with self.engine.connect() as db:
            run = await store.generation(db, identifier)
        await self.authorized(actor, run.conversation_id)
        async with self.engine.begin() as db:
            await store.conversation(db, run.conversation_id, lock=True)
            current = await store.generation(db, identifier, lock=True)
            if current.status in {"QUEUED", "RUNNING"}:
                await store.stop(db, current, "CANCELLED", None)
        return await self.get_generation(actor, identifier)

    async def calls(self, actor: VerifiedIdentity, identifier: str) -> CallsView:
        """复核本人及全部来源后读取有界调用摘要，不返回参数正文和完整Prompt。"""
        output = await self.get_generation(actor, identifier)
        async with self.engine.connect() as db:
            models = await queries.model_calls(db, {"id": identifier})
            tools = await queries.tool_calls(db, {"id": identifier})
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
            tool_calls=[
                ToolCallView(
                    number=row["tool_number"],
                    name=row["tool_name"],
                    status=row["status"],
                    result_summary=None if output.sources_hidden else row["result_summary"],
                    source_usage_ids=[]
                    if output.sources_hidden
                    else json.loads(row["result_refs"])["source_usage_ids"],
                )
                for row in tools
            ],
        )
