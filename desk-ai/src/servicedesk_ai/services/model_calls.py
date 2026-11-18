"""Chat及查询/索引Embedding的统一持久账目与执行权边界。作者：yangaobo。"""

from __future__ import annotations

import asyncio
import hashlib
import json
from dataclasses import dataclass
from decimal import Decimal
from typing import Literal

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine

from servicedesk_ai.diagnosis.budget import (
    ESTIMATOR_VERSION,
    ExecutionBudgetExceeded,
    estimate_input_tokens,
)
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.run import RunErrorCode
from servicedesk_ai.infrastructure.models import (
    EmbeddingResponse,
    ModelClient,
    ModelMessage,
    ModelProtocolError,
    ModelProviderError,
    ModelRequest,
    ModelResponse,
    ModelUsage,
)
from servicedesk_ai.repositories import index_jobs, runs, tasks
from servicedesk_ai.repositories.call_usage import require_budget, usage
from servicedesk_ai.repositories.values import json_value, new_id
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.schemas.model_calls import ModelInputManifest, PricingSnapshot


@dataclass(frozen=True)
class RecordedResponse:
    """只有数据库成功提交后才交给执行器使用模型结果。"""

    call_id: str
    response: ModelResponse


class ModelCalls:
    """每次真实调用单独编号，UNKNOWN永不被当作零用量或可自动重发。"""

    def __init__(
        self,
        engine: AsyncEngine,
        model: ModelClient,
        pricing: PricingSnapshot,
        embedding_pricing: PricingSnapshot | None = None,
    ) -> None:
        """资源由worker关闭，计价配置每次预留固定到调用账目。"""
        self.engine, self.model, self.pricing = engine, model, pricing
        self.embedding_pricing = embedding_pricing or PricingSnapshot()

    async def prepare(
        self,
        run: runs.RunLease,
        request: ModelRequest,
        source_ids: list[str],
        *,
        review_of_call_id: str | None = None,
    ) -> str:
        """预留前同时检查本轮与跨轮累计预算，当前Run锁串行化并发调用。"""
        inputs = estimate_input_tokens(request)
        outputs = request.max_output_tokens or self.model.settings.max_output_tokens
        if (
            inputs > run.pipeline_config.max_input_tokens
            or outputs > run.pipeline_config.max_output_tokens
        ):
            raise ExecutionBudgetExceeded("本次模型请求超过单轮输入或输出预算")
        reserved = inputs + outputs
        manifest = ModelInputManifest(
            request_sha256=canonical_hash(request.model_dump(mode="json")),
            estimator_version=ESTIMATOR_VERSION,
            input_tokens_upper=inputs,
            output_tokens_limit=outputs,
            source_usage_ids=source_ids,
            review_of_call_id=review_of_call_id,
        )
        return await self.reserve(
            run,
            "CHAT",
            request.model or self.model.settings.chat_model,
            manifest,
            reserved,
            self.pricing,
        )

    async def own(self, db: AsyncConnection, parent: runs.RunLease | index_jobs.IndexJob) -> None:
        """每次意图和回执都比较所属任务的原令牌，不能用进程身份代替租约。"""
        if isinstance(parent, runs.RunLease):
            await runs.owned(db, parent)
        else:
            await index_jobs.owned(db, parent)

    async def reserve(
        self,
        parent: runs.RunLease | index_jobs.IndexJob,
        purpose: Literal["CHAT", "QUERY_EMBEDDING", "INDEX_EMBEDDING"],
        model: str,
        manifest: ModelInputManifest,
        tokens: int,
        pricing: PricingSnapshot,
    ) -> str:
        """统一记录Chat及Embedding意图；输入清单不包含原文，索引预算由固定片段上限约束。"""
        cost = pricing.cost(manifest.input_tokens_upper, manifest.output_tokens_limit)
        async with self.engine.begin() as db:
            task = (
                await tasks.task(db, parent.task_id, lock=True)
                if isinstance(parent, runs.RunLease) and parent.task_id
                else None
            )
            await self.own(db, parent)
            if task and isinstance(parent, runs.RunLease):
                if task.task_state_version != parent.input_state_version:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                if pricing.currency != task.budget_policy.currency:
                    raise BusinessError(RunErrorCode.TASK_BUDGET_EXHAUSTED)
                require_budget(
                    await usage(db, task_id=task.id), task.budget_policy, tokens=tokens, cost=cost
                )
            scope_column = "run_id" if isinstance(parent, runs.RunLease) else "index_job_id"
            if isinstance(parent, runs.RunLease):
                current = await usage(db, run_id=parent.id)
                if (
                    current.unsettled
                    or current.calls >= parent.pipeline_config.max_model_calls
                    or current.tokens + tokens > parent.pipeline_config.total_tokens
                ):
                    raise ExecutionBudgetExceeded("本轮调用未结算或已达到调用及Token预算")
            else:
                # 原批次未知时只允许对账，不能因重领租约重新付费。
                pending = (
                    await db.execute(
                        text("""
                    SELECT id FROM ai_model_call WHERE index_job_id=:id
                      AND status IN ('PREPARED','IN_FLIGHT','UNKNOWN') LIMIT 1
                """),
                        {"id": parent.id},
                    )
                ).first()
                if pending:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                totals = (
                    await db.execute(
                        text("""
                    SELECT COUNT(*),COALESCE(SUM(COALESCE(input_tokens,reserved_tokens)),0)
                    FROM ai_model_call WHERE index_job_id=:id AND status<>'CANCELLED'
                """),
                        {"id": parent.id},
                    )
                ).one()
                if (
                    totals[0] >= parent.processing_config.max_chunks
                    or totals[1] + tokens > parent.processing_config.max_chunks * 16000
                ):
                    raise ExecutionBudgetExceeded("索引调用已达到固定片段及Token预算")
            number = (
                await db.execute(
                    text(
                        "SELECT COALESCE(MAX(call_number),0)+1 FROM ai_model_call "
                        f"WHERE {scope_column}=:id"
                    ),
                    {"id": parent.id},
                )
            ).scalar_one()
            identifier = new_id()
            await db.execute(
                text(f"""
                INSERT INTO ai_model_call(id,{scope_column},call_number,purpose,
                    claim_token,provider,
                    requested_model,input_manifest,reserved_tokens,reserved_cost,pricing_snapshot)
                VALUES (:id,:parent,:number,:purpose,:token,'DASHSCOPE',:model,
                    :manifest,:tokens,:cost,:pricing)
            """),
                {
                    "id": identifier,
                    "parent": parent.id,
                    "number": number,
                    "purpose": purpose,
                    "token": parent.claim_token,
                    "model": model,
                    "manifest": manifest.model_dump_json(),
                    "tokens": tokens,
                    "cost": cost,
                    "pricing": pricing.model_dump_json(),
                },
            )
            if isinstance(parent, runs.RunLease):
                if purpose == "CHAT":
                    await db.execute(
                        text("""
                        UPDATE ai_run SET execution_phase='MODEL',
                            iteration_number=iteration_number+1,
                            updated_at=UTC_TIMESTAMP(6) WHERE id=:id
                    """),
                        {"id": parent.id},
                    )
                for source_id in manifest.source_usage_ids:
                    result = await db.execute(
                        text("""
                        UPDATE ai_source_usage
                        SET first_model_call_id=COALESCE(first_model_call_id,:call)
                        WHERE id=:id AND run_id=:run
                    """),
                        {"id": source_id, "call": identifier, "run": parent.id},
                    )
                    if result.rowcount != 1:
                        raise BusinessError(CommonErrorCode.CONFLICT)
        return identifier

    async def dispatch(self, run: runs.RunLease | index_jobs.IndexJob, identifier: str) -> None:
        """提交IN_FLIGHT后才发送；提交未知时不发送，也不假定已可退款。"""
        async with self.engine.begin() as db:
            await self.own(db, run)
            result = await db.execute(
                text("""
                UPDATE ai_model_call SET status='IN_FLIGHT',started_at=UTC_TIMESTAMP(6)
                WHERE id=:id AND (run_id=:run OR index_job_id=:run)
                AND claim_token=:token AND status='PREPARED'
            """),
                {"id": identifier, "run": run.id, "token": run.claim_token},
            )
            if result.rowcount != 1:
                raise BusinessError(CommonErrorCode.CONFLICT)

    async def complete(
        self,
        run: runs.RunLease | index_jobs.IndexJob,
        identifier: str,
        response: ModelResponse | EmbeddingResponse | ModelProtocolError,
    ) -> None:
        """供应商已响应的非法输出仍结算真实用量；失去令牌的迟到结果不能提交。"""
        metering = response.usage or ModelUsage()
        valid = isinstance(response, (ModelResponse, EmbeddingResponse))
        payload = (
            json_value({"schema_version": 1, "response": response.model_dump(mode="json")})
            if isinstance(response, ModelResponse)
            else None
        )
        if isinstance(response, EmbeddingResponse):
            payload = json_value(
                {
                    "schema_version": 1,
                    "embedding": {
                        "count": len(response.vectors),
                        "dimensions": self.model.settings.dimensions,
                        "vectors_sha256": hashlib.sha256(
                            json.dumps(response.vectors, separators=(",", ":")).encode()
                        ).hexdigest(),
                    },
                }
            )
        if isinstance(response, ModelProtocolError):
            # 原因来自适配器固定文案，不保存供应商原文、工具参数或隐藏推理。
            payload = json_value({"schema_version": 1, "protocol_error": str(response)})
        if payload and len(payload.encode("utf-8")) > 64 * 1024:
            valid, payload = False, None
        async with self.engine.begin() as db:
            await self.own(db, run)
            raw = (
                await db.execute(
                    text("""
                SELECT pricing_snapshot FROM ai_model_call
                WHERE id=:id AND (run_id=:run OR index_job_id=:run)
                AND claim_token=:token AND status='IN_FLIGHT'
                FOR UPDATE
            """),
                    {"id": identifier, "run": run.id, "token": run.claim_token},
                )
            ).scalar_one_or_none()
            if raw is None:
                raise BusinessError(CommonErrorCode.CONFLICT)
            pricing = PricingSnapshot.model_validate_json(raw)
            cost = None
            if metering.input_tokens is not None and metering.output_tokens is not None:
                cost = pricing.cost(
                    metering.input_tokens, metering.output_tokens, metering.cached_input_tokens or 0
                )
            await db.execute(
                text("""
                UPDATE ai_model_call SET status=:status,response_payload=:payload,
                    input_tokens=:inputs,output_tokens=:outputs,cached_input_tokens=:cached,
                    estimated_cost=:cost,provider_request_id=:provider,response_model=:model,
                    error_code=:error,finished_at=UTC_TIMESTAMP(6) WHERE id=:id
            """),
                {
                    "id": identifier,
                    "status": "SUCCEEDED" if valid else "FAILED",
                    "payload": payload,
                    "inputs": metering.input_tokens,
                    "outputs": metering.output_tokens,
                    "cached": metering.cached_input_tokens,
                    "cost": cost,
                    "provider": response.provider_request_id,
                    "model": response.model,
                    "error": None if valid else "INVALID_MODEL_RESULT",
                },
            )
        if not valid and isinstance(response, ModelResponse):
            raise ModelProtocolError(
                "模型公开结果超过持久预算",
                response.usage,
                response.provider_request_id,
                response.model,
            )
        if isinstance(run, runs.RunLease):
            # 已发生费用先如实提交，再拒绝交付超预算结果；不能回滚账目掩盖实际消耗。
            async with self.engine.connect() as db:
                totals = await usage(db, run_id=run.id)
                if totals.tokens > run.pipeline_config.total_tokens:
                    raise ExecutionBudgetExceeded("本轮实际用量超过Token预算，已保存真实账目")
                if run.task_id:
                    task = await tasks.task(db, run.task_id)
                    require_budget(
                        await usage(db, task_id=task.id),
                        task.budget_policy,
                        tokens=0,
                        cost=Decimal(0),
                        calls=0,
                    )

    async def provider_failure(
        self, run: runs.RunLease | index_jobs.IndexJob, identifier: str, error: ModelProviderError
    ) -> None:
        """明确拒绝与未知结果分开持久化；供应商未返回用量时不猜零费用。"""
        if not error.rejected:
            await self.unknown(run, identifier)
            return
        async with self.engine.begin() as db:
            await self.own(db, run)
            await db.execute(
                text("""
                UPDATE ai_model_call SET status='FAILED',error_code='PROVIDER_REJECTED',
                    response_payload=:payload,finished_at=UTC_TIMESTAMP(6)
                WHERE id=:id AND (run_id=:run OR index_job_id=:run)
                    AND claim_token=:token AND status='IN_FLIGHT'
                """),
                {
                    "id": identifier,
                    "run": run.id,
                    "token": run.claim_token,
                    "payload": json_value({"schema_version": 1, "provider_error": error.reason}),
                },
            )

    async def unknown(self, run: runs.RunLease | index_jobs.IndexJob, identifier: str) -> None:
        """仅仍有效执行权可收敛在途调用；取消后的UNKNOWN已由父终态事务保存。"""
        async with self.engine.begin() as db:
            try:
                await self.own(db, run)
            except BusinessError:
                return
            await db.execute(
                text("""
                UPDATE ai_model_call SET status='UNKNOWN',error_code='PROVIDER_RESULT_UNKNOWN',
                    finished_at=UTC_TIMESTAMP(6)
                WHERE id=:id AND (run_id=:run OR index_job_id=:run)
                AND claim_token=:token AND status='IN_FLIGHT'
            """),
                {"id": identifier, "run": run.id, "token": run.claim_token},
            )

    async def embed(
        self,
        parent: runs.RunLease | index_jobs.IndexJob,
        texts: list[str],
        source_ids: list[str] | None = None,
    ) -> EmbeddingResponse:
        """统一Embedding外调；账目只保存批次指纹和维度，向量由索引适配器消费。"""
        if not 1 <= len(texts) <= self.model.settings.embedding_batch_size or any(
            not value.strip() for value in texts
        ):
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        request = ModelRequest(
            messages=tuple(ModelMessage(role="user", content=value) for value in texts)
        )
        inputs = estimate_input_tokens(request)
        manifest = ModelInputManifest(
            request_sha256=canonical_hash(
                {
                    "texts": texts,
                    "model": self.model.settings.embedding_model,
                    "dimensions": self.model.settings.dimensions,
                }
            ),
            estimator_version=ESTIMATOR_VERSION,
            input_tokens_upper=inputs,
            output_tokens_limit=0,
            source_usage_ids=source_ids or [],
        )
        purpose: Literal["QUERY_EMBEDDING", "INDEX_EMBEDDING"] = (
            "QUERY_EMBEDDING" if isinstance(parent, runs.RunLease) else "INDEX_EMBEDDING"
        )
        identifier = await self.reserve(
            parent,
            purpose,
            self.model.settings.embedding_model,
            manifest,
            inputs,
            self.embedding_pricing,
        )
        await self.dispatch(parent, identifier)
        try:
            response = await self.model.embed(texts)
        except ModelProviderError as error:
            await self.provider_failure(parent, identifier, error)
            raise
        except ModelProtocolError as error:
            await self.complete(parent, identifier, error)
            raise
        except (Exception, asyncio.CancelledError):
            await self.unknown(parent, identifier)
            raise
        await self.complete(parent, identifier, response)
        return response

    async def chat(
        self,
        run: runs.RunLease,
        request: ModelRequest,
        source_ids: list[str],
        *,
        review_of_call_id: str | None = None,
    ) -> RecordedResponse:
        """唯一Chat外调入口；调用失败不重试，返回前保证账目已持久提交。"""
        request = request.model_copy(
            update={
                "model": run.pipeline_config.model,
                "max_output_tokens": run.pipeline_config.max_output_tokens,
            }
        )
        identifier = await self.prepare(
            run, request, source_ids, review_of_call_id=review_of_call_id
        )
        await self.dispatch(run, identifier)
        try:
            response = await self.model.chat(request)
        except ModelProviderError as error:
            await self.provider_failure(run, identifier, error)
            raise
        except ModelProtocolError as error:
            await self.complete(run, identifier, error)
            raise
        except (Exception, asyncio.CancelledError):
            await self.unknown(run, identifier)
            raise
        await self.complete(run, identifier, response)
        return RecordedResponse(identifier, response)
