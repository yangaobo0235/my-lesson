"""单一Run执行器：有界只读循环、持久调用与原子任务提交。作者：yangaobo。"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from dataclasses import dataclass
from datetime import UTC, datetime

import grpc
from opentelemetry import trace
from pydantic import ValidationError
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine

from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.diagnosis.budget import (
    FINALIZATION_HEADROOM,
    ExecutionBudgetExceeded,
    compact_tool_context,
    estimate_input_tokens,
)
from servicedesk_ai.diagnosis.grounding import GroundingRejected, GroundingReview, review_request
from servicedesk_ai.diagnosis.model_request import (
    DISCOVERY_PHASE,
    correction_messages,
    discovery_tools,
    evidence_context,
    final_model_request,
    needs_model_review,
    procedure_availability,
)
from servicedesk_ai.diagnosis.run_context import RunContextBuilder
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.run import RunErrorCode
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc
from servicedesk_ai.infrastructure.identity import IdentityFailure, translate_failure
from servicedesk_ai.infrastructure.models import (
    ModelMessage,
    ModelProviderError,
    ModelRequest,
    ModelTool,
    ToolProposalRejected,
)
from servicedesk_ai.infrastructure.rpc_contract import request_budget
from servicedesk_ai.infrastructure.tracing import parent
from servicedesk_ai.repositories import runs, tasks
from servicedesk_ai.repositories.call_usage import usage
from servicedesk_ai.schemas.runs import (
    ProcedureConclusion,
    ProcedureResult,
    RunResult,
)
from servicedesk_ai.schemas.tasks import (
    AnswerSchema,
    CheckProposal,
)
from servicedesk_ai.services.evidence import Evidence, EvidenceService, actor_ref
from servicedesk_ai.services.model_calls import ModelCalls, RecordedResponse
from servicedesk_ai.services.run_completion import (
    ModelOutputRejected,
    RunCompletion,
)
from servicedesk_ai.services.run_tools import RunTools
from servicedesk_ai.services.task_access import TaskAccess

LOGGER = logging.getLogger(__name__)


def reports_hazard(statement: str) -> bool:
    """明确电气风险反馈优先停止；否定及询问有无不当作阳性观测。"""
    for clause in re.split(r"[，,。；;！!？?\n]", statement):
        for match in re.finditer(r"焦味|烧焦味|冒烟|电火花|漏电|电池鼓包|异常发热", clause):
            prefix = clause[max(0, match.start() - 8) : match.start()]
            if not re.search(
                r"(?:没有|并无|无|未|否认|是否|有无|有否)"
                r"(?:出现|发现|闻到|看到|报告|检测到|存在|有)?(?:明显的?)?\s*$",
                prefix,
            ):
                return True
    return False


@dataclass(frozen=True)
class ToolRound:
    """工具回执及宿主的无进展标记；标记来自工具协议，不读取资料正文中的指令。"""

    messages: list[ModelMessage]
    finalize: bool = False


@dataclass(frozen=True)
class ModelTurn:
    """本次调用的固定请求、收束阶段及审核规程可用性。"""

    request: ModelRequest
    finalizing: bool
    procedure_mode: bool
    unavailable: dict[int, str]


class RunWorker:
    """MySQL持有唯一执行权；等待用户即完成本轮，不在后台循环等待答复。"""

    def __init__(
        self,
        engine: AsyncEngine,
        rpc: DomainRpc,
        settings: Settings,
        evidence: EvidenceService,
        models: ModelCalls,
        tools: RunTools,
        tracer: trace.Tracer,
    ) -> None:
        """长连接由进程作用域关闭，用例不共享并发事务连接。"""
        self.engine, self.rpc, self.settings = engine, rpc, settings
        self.evidence, self.models, self.tools = evidence, models, tools
        self.tracer = tracer
        self.access = TaskAccess(engine, rpc)
        self.completion = RunCompletion(engine, self.access)
        self.context_builder = RunContextBuilder(engine, evidence, tools)

    async def heartbeat(self, run: runs.RunLease) -> None:
        """租约或总Deadline失效时停止等待，数据库再次阻止迟到提交。"""
        while True:
            await asyncio.sleep(run.pipeline_config.renewal_seconds)
            async with self.engine.begin() as db:
                if not await runs.renew(db, run):
                    raise BusinessError(CommonErrorCode.CONFLICT)

    async def run_one(self) -> bool:
        """只领取QUEUED，不将中断的未知付费请求自动重排。"""
        async with self.engine.begin() as db:
            run = await runs.claim(db)
        if run is None:
            return False
        heartbeat = asyncio.create_task(self.heartbeat(run))
        execution = asyncio.create_task(self.execute(run))
        try:
            done, _ = await asyncio.wait(
                {heartbeat, execution}, return_when=asyncio.FIRST_COMPLETED
            )
            for pending in done:
                await pending
        except asyncio.CancelledError:
            async with self.engine.begin() as db:
                await runs.stop(db, run.id, "INTERRUPTED", "WORKER_STOPPED", token=run.claim_token)
            raise
        except Exception as error:
            if isinstance(error, grpc.aio.AioRpcError):
                error = translate_failure(error)
            code = (
                "DEPENDENCY_UNAVAILABLE"
                if isinstance(error, IdentityFailure) and error.code in {1011, 1012, 1013}
                else "MODEL_PROVIDER_UNAVAILABLE"
                if isinstance(error, ModelProviderError)
                else "TASK_BUDGET_EXHAUSTED"
                if isinstance(error, BusinessError)
                and error.error == RunErrorCode.TASK_BUDGET_EXHAUSTED
                else "EXECUTION_BUDGET_EXCEEDED"
                if isinstance(error, (TimeoutError, ExecutionBudgetExceeded))
                else (
                    "CONTEXT_CHANGED"
                    if isinstance(error, BusinessError) and error.error == CommonErrorCode.CONFLICT
                    else "RUN_EXECUTION_FAILED"
                )
            )
            LOGGER.warning(
                "运行结束 runId=%s errorCode=%s errorType=%s businessCode=%s",
                run.id,
                code,
                type(error).__name__,
                error.error.code
                if isinstance(error, BusinessError)
                else error.code
                if isinstance(error, IdentityFailure)
                else None,
                extra={
                    "event": "run_failed",
                    "runId": run.id,
                    "taskId": run.task_id,
                    "errorCode": code,
                },
            )
            async with self.engine.begin() as db:
                await runs.stop(db, run.id, "FAILED", code, token=run.claim_token)
        finally:
            heartbeat.cancel()
            execution.cancel()
            await asyncio.gather(heartbeat, execution, return_exceptions=True)
        return True

    async def execute(self, run: runs.RunLease) -> None:
        """总墙钟包括排队；所有子RPC、模型和工具共享剩余父预算。"""
        remaining = (run.deadline_at.replace(tzinfo=UTC) - datetime.now(UTC)).total_seconds()
        if remaining <= 0:
            raise TimeoutError("运行总预算已到期")
        with (
            request_budget(remaining),
            self.tracer.start_as_current_span(
                "ai.run.execute",
                context=parent(run.trace_context),
                attributes={"run.id": run.id, "conversation.id": run.conversation_id},
                record_exception=False,
                set_status_on_exception=False,
            ),
        ):
            async with asyncio.timeout(remaining):
                LOGGER.info(
                    "开始执行诊断运行",
                    extra={"event": "run_started", "runId": run.id, "taskId": run.task_id},
                )
                await self.execute_bounded(run)
                LOGGER.info(
                    "诊断结果已提交",
                    extra={"event": "run_completed", "runId": run.id, "taskId": run.task_id},
                )

    async def verify(self, run: runs.RunLease) -> None:
        """每轮外调前重核原Session、当前业务范围和环境；快照不能授权新工具。"""
        actor = await self.rpc.identity.verify_ref(actor_ref(run))
        conversation = await self.access.conversation(actor, run.conversation_id)
        if run.task_id:
            async with self.engine.connect() as db:
                task = await tasks.task(db, run.task_id)
            _, context = await self.access.authorize(actor, task, new_execution=True)
            if (
                task.task_state_version != run.input_state_version
                or (task.diagnosis_context_version != run.diagnosis_context_version)
                or (
                    conversation.ticket_id
                    and context.diagnosis_context_version != run.diagnosis_context_version
                )
            ):
                raise BusinessError(CommonErrorCode.CONFLICT)
        elif conversation.mode != "CONSULT":
            raise BusinessError(CommonErrorCode.CONFLICT)
        await self.evidence.verify(run)

    async def execute_bounded(self, run: runs.RunLease) -> None:
        """咨询不注册业务工具；排障以有界证据和结构化检查结束本轮。"""
        await self.verify(run)
        if run.task_id:
            async with self.engine.connect() as db:
                conditions = await tasks.reported_conditions(db, run.task_id)
            if conditions:
                await self.finish(
                    run,
                    RunResult(
                        answer_type="HANDOVER",
                        text=(
                            "本任务有尚未更正或撤回的人工停止条件报告，已停止操作指引和模型调用。"
                            "请联系有权限的支持人员核对风险、权限或矛盾证据；"
                            "如原报告有误，请明确更正该事实后再继续，不能把没有新报告当作风险解除。"
                        ),
                    ),
                )
                return
        if run.task_id and await self.hazard_report(run):
            await self.finish(
                run,
                RunResult(
                    answer_type="HANDOVER",
                    text="反馈中包含电气危险信号。请停止使用与排查，勿拆机或重复通电，在安全位置联系现场专业人员；这不是工具实测或已修复结论。",
                ),
            )
            return
        run, messages = await self.context_builder.build(run)
        environment = run.context_manifest.support_context
        if (
            run.task_id
            and not run.context_manifest.checks
            and (
                not run.context_manifest.service_item_id
                or environment is None
                or not (environment.os_name or environment.software_name)
            )
        ):
            await self.finish(
                run,
                RunResult(
                    answer_type="CLARIFICATION",
                    text="请补充当前系统或软件名称、版本，以及准确的支持项目；不确定的信息保持未知。",
                    checks=[
                        CheckProposal(
                            check_key="support_environment",
                            check_kind="CLARIFICATION",
                            instruction="当前使用什么系统或软件及版本？",
                            answer_schema=AnswerSchema(kind="TEXT"),
                            dependency_fields=["os_name", "software_name"],
                        )
                    ],
                ),
            )
            return
        definitions = self.tools.definitions(run)
        finalizing = not definitions
        corrections = 0
        for iteration in range(run.pipeline_config.max_model_calls):
            await self.verify(run)
            evidence = await self.evidence.list_items(run.id)
            turn = await self.plan_model_turn(
                run, messages, evidence, definitions, iteration, finalizing
            )
            finalizing = turn.finalizing
            request = turn.request
            if finalizing:
                if run.task_id and not await self.completion.has_diagnostic_basis(run, evidence):
                    # 提问、环境自述和召回预览不能支撑自由生成的排障建议；不做关键词过滤。
                    await self.finish(
                        run,
                        RunResult(
                            answer_type="INSUFFICIENT_EVIDENCE",
                            text="当前缺少完整知识、有效诊断记录或检查反馈，无法提供有依据的排障建议。"
                            "请补充具体故障现象或联系支持人员核对资料；普通概念问题可使用普通咨询。",
                        ),
                    )
                    return
            if estimate_input_tokens(request) > run.pipeline_config.max_input_tokens:
                LOGGER.info(
                    "上下文预算耗尽 runId=%s estimatedTokens=%s messageCount=%s",
                    run.id,
                    estimate_input_tokens(request),
                    len(request.messages),
                )
                await self.finish(
                    run,
                    RunResult(
                        answer_type="INSUFFICIENT_EVIDENCE",
                        text="本轮完整证据已达到上下文预算，未继续调用模型。请缩小排查问题或转人工核对现有资料。",
                    ),
                )
                return
            try:
                recorded = await self.models.chat(
                    run,
                    request,
                    [item.id for item in evidence],
                )
            except ToolProposalRejected as error:
                if (
                    finalizing
                    or not run.task_id
                    or error.usage is None
                    or error.usage.input_tokens is None
                    or error.usage.output_tokens is None
                ):
                    raise
                # 账目已经记FAILED并结算确认用量；整批工具均未执行，不采纳或重试任何提议。
                # 关闭取证能力后仅凭此前有效依据收束，后续仍受原预算、依据门槛及提交核验约束。
                await self.verify(run)
                finalizing = True
                messages.append(
                    ModelMessage(
                        role="system",
                        content="本轮上一批工具提议被宿主拒绝，没有执行任何工具，也没有取得新证据。"
                        "取证阶段已结束；仅依据此前实际取得的有效资料按最终Schema回答。"
                        "证据不足须明确说明，不假装已完成被拒绝的取证或操作。",
                    )
                )
                continue
            response = recorded.response
            if not response.message.tool_calls:
                if not finalizing:
                    # 无工具提议只结束取证；中间文本不是已校验回答，也不成为后续指令。
                    finalizing = True
                    continue
                try:
                    await self.commit_candidate(run, recorded, turn, evidence)
                except ExecutionBudgetExceeded:
                    await self.finish(
                        run,
                        RunResult(
                            answer_type="INSUFFICIENT_EVIDENCE",
                            text="本轮剩余预算不足以完成回答依据核验，候选建议未发布。请缩小问题或转人工核对资料。",
                        ),
                    )
                    return
                except (ValidationError, ModelOutputRejected) as error:
                    # 仅输出合同可纠正；身份、环境、租约、数据库失败仍立即中止。
                    if corrections:
                        if not run.task_id:
                            raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR) from error
                        await self.finish(
                            run,
                            RunResult(
                                answer_type="INSUFFICIENT_EVIDENCE",
                                text="本轮回答未通过依据或输出校验，未发布未经核实的判断和操作建议。请补充适用条件或由支持人员核对原资料。",
                            ),
                        )
                        return
                    corrections += 1
                    await self.verify(run)
                    messages.extend(correction_messages(error))
                    continue
                return
            # 账目保留完整模型回执；后续上下文只继承工具协议，未校验自述不是事实。
            messages.append(response.message.model_copy(update={"content": None}))
            if (
                response.usage is None
                or response.usage.input_tokens is None
                or response.usage.output_tokens is None
            ):
                raise ExecutionBudgetExceeded("上一模型调用用量未知，不能继续付费取证")
            round_result = await self.tool_messages(run, recorded.call_id, response.message)
            messages.extend(round_result.messages)
            if not round_result.finalize and await self.tools.no_progress(run):
                # 没有新增来源仅说明取证应停止，不能否定此前已取得的完整依据。
                # 下一轮统一复核执行权、预算和依据，再进入不含工具的最终阶段。
                finalizing = True
            finalizing |= round_result.finalize
        raise ExecutionBudgetExceeded("本轮模型循环已达到次数预算")

    async def plan_model_turn(
        self,
        run: runs.RunLease,
        messages: list[ModelMessage],
        evidence: list[Evidence],
        definitions: list[ModelTool],
        iteration: int,
        finalizing: bool,
    ) -> ModelTurn:
        """按现有证据、规程资格及调用账目决定取证或收束，计算实际发送的请求。"""
        current, candidate_directory = evidence_context(messages, evidence)
        environment = run.context_manifest.support_context
        procedure_mode = any(
            item.source_type == "PROCEDURE_CHECK"
            or (
                item.source_type == "CHUNK"
                and isinstance(source := item.source_version.get("knowledge_source"), dict)
                and bool(source.get("procedure_check_keys"))
            )
            for item in evidence
        )
        unavailable = (
            await self.completion.unavailable_procedure_checks(run, evidence)
            if procedure_mode
            else {}
        )
        if procedure_mode:
            current.append(procedure_availability(evidence, unavailable))
        final_request = final_model_request(
            current,
            procedure_mode,
            evidence,
            set(unavailable),
            run.diagnosis_context_version,
            environment,
        )
        if (
            estimate_input_tokens(final_request) + FINALIZATION_HEADROOM
            > run.pipeline_config.max_input_tokens
        ):
            current = compact_tool_context(current)
            final_request = final_model_request(
                current,
                procedure_mode,
                evidence,
                set(unavailable),
                run.diagnosis_context_version,
                environment,
            )
        async with self.engine.connect() as db:
            spent = await usage(db, run_id=run.id)
            task_usage = await usage(db, task_id=run.task_id) if run.task_id else None
            task = await tasks.task(db, run.task_id) if run.task_id else None
        finalizing |= (
            estimate_input_tokens(final_request) + FINALIZATION_HEADROOM
            > run.pipeline_config.max_input_tokens
            or spent.calls >= run.pipeline_config.max_model_calls - (4 if run.task_id else 2)
            or iteration == run.pipeline_config.max_model_calls - 1
            or (
                task is not None
                and task_usage is not None
                and task_usage.calls >= task.budget_policy.max_model_calls - 4
            )
        )
        remaining_calls = run.pipeline_config.max_model_calls - spent.calls
        if task is not None and task_usage is not None:
            remaining_calls = min(
                remaining_calls, task.budget_policy.max_model_calls - task_usage.calls
            )
        active_tools, required_tool = discovery_tools(definitions, evidence, remaining_calls)
        finalizing |= not active_tools
        discovery_phase = ModelMessage(
            role="system",
            content=(DISCOVERY_PHASE.content or "")
            + "本次仅可提议以下工具："
            + ",".join(tool.name for tool in active_tools)
            + "。先前回执中出现的工具不代表本次仍可用；未列出的工具禁止提议。"
            + "当前工具无法取得必要依据时结束取证，最终说明缺口，不虚构其他工具。",
        )
        request = ModelRequest(
            messages=(*current, *candidate_directory, discovery_phase),
            tools=active_tools,
            required_tool=required_tool,
            parallel_tool_calls=not any(tool.name == "search_knowledge" for tool in active_tools),
        )
        if estimate_input_tokens(request) > run.pipeline_config.max_input_tokens:
            current = compact_tool_context(current)
            request = request.model_copy(
                update={"messages": (*current, *candidate_directory, discovery_phase)}
            )
            final_request = final_model_request(
                current,
                procedure_mode,
                evidence,
                set(unavailable),
                run.diagnosis_context_version,
                environment,
            )
            # 工具合同装不下时仍可按完整证据收束，不因取证协议较大而跳过合法最终请求。
            finalizing |= estimate_input_tokens(request) > run.pipeline_config.max_input_tokens

        return ModelTurn(
            final_request if finalizing else request, finalizing, procedure_mode, unavailable
        )

    async def commit_candidate(
        self,
        run: runs.RunLease,
        recorded: RecordedResponse,
        turn: ModelTurn,
        evidence: list[Evidence],
    ) -> None:
        """校验模型候选和恢复反馈来源，再交给同一持久提交用例。"""
        response = recorded.response
        procedure = (
            ProcedureResult.model_validate_json(response.message.content or "")
            if turn.procedure_mode
            else None
        )
        result = (
            await self.completion.prepare_procedure_result(
                run,
                procedure,
                evidence,
            )
            if procedure is not None
            else RunResult.model_validate_json(response.message.content or "")
        )
        if run.task_id and needs_model_review(result, procedure is not None):
            result = await self.review_proposal(
                run,
                recorded.call_id,
                result,
                evidence,
                turn.unavailable,
                procedure_mode=procedure is not None,
            )
        conclusion = procedure.next_action if procedure is not None else None
        if (
            isinstance(conclusion, ProcedureConclusion)
            and conclusion.feedback_source_number is not None
        ):
            await self.finish(run, result, recovery_source_number=conclusion.feedback_source_number)
        else:
            await self.finish(run, result)

    async def review_proposal(
        self,
        run: runs.RunLease,
        candidate_call_id: str,
        result: RunResult,
        evidence: list[Evidence],
        unavailable: dict[int, str],
        *,
        procedure_mode: bool,
    ) -> RunResult:
        """自由内容与审核规程各守自己的验收边界；可选分析失败不能否决独立有效的检查。

        仅规程宿主输出可移除整组可选分析，不按审核模型的逐句判断拼接部分建议。
        自由澄清和普通回答仍整份纠正或拒绝；协议、授权和依赖失败向上传递。
        返回值尚未提交，原来源、检查前提及恢复反馈仍由最终事务重新校验。
        """
        candidate = result
        if procedure_mode:
            candidate = result.model_copy(
                update={
                    "text": "",
                    "checks": [
                        item for item in result.checks if item.check_kind == "CLARIFICATION"
                    ],
                }
            )
        try:
            await self.review_answer(run, candidate_call_id, candidate, evidence, unavailable)
        except (GroundingRejected, ExecutionBudgetExceeded):
            if not procedure_mode or candidate.checks:
                raise
            # 原候选和已发生的核验仍保留于调用账目；不发布无依据内容、不修改已有假设。
            return RunResult.model_validate(
                result.model_dump()
                | {
                    "hypotheses": [],
                    "proposed_record": None,
                    "proposed_resolution": None,
                    "resolution_type": None,
                    "referral_target": None,
                }
            )
        return result

    async def review_answer(
        self,
        run: runs.RunLease,
        candidate_call_id: str,
        result: RunResult,
        evidence: list[Evidence],
        unavailable: dict[int, str] | None = None,
    ) -> None:
        """候选不落公开结果；独立核验仍走原预算、租约、授权和逐次调用账目。"""
        async with self.engine.connect() as db:
            question = (
                await db.execute(
                    text(
                        "SELECT content FROM ai_message "
                        "WHERE id=:id AND conversation_id=:conversation"
                    ),
                    {"id": run.input_message_id, "conversation": run.conversation_id},
                )
            ).scalar_one()
        request, source_ids = review_request(run, question, result, evidence, unavailable)
        await self.verify(run)
        recorded = await self.models.chat(
            run, request, source_ids, review_of_call_id=candidate_call_id
        )
        try:
            review = GroundingReview.model_validate_json(recorded.response.message.content or "")
            contexts = {"request", "taskGoal"}
            if run.context_manifest.support_context is not None:
                contexts.add("environment")
            if run.context_manifest.ticket_snapshot is not None:
                contexts.add("ticket")
            review.validate_coverage(
                result, [item for item in evidence if item.id in source_ids], contexts
            )
        except ValueError:
            # 审核协议失败不能归咎于候选答案，更不能再付费要求生成器修改合法候选。
            raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR) from None
        if not review.accepted:
            raise GroundingRejected(review, result)

    async def tool_messages(
        self, run: runs.RunLease, model_call_id: str, message: ModelMessage
    ) -> ToolRound:
        """独立只读工具有界并行；含付费Embedding的搜索串行，不共享事务连接。

        同一提议内工具没有后续结果引用；回执按模型提议顺序组装。任一执行权
        错误取消同批在途工具，所有结果仍经所属Run令牌核对，不发布部分回答。
        """
        semaphore = asyncio.Semaphore(run.pipeline_config.readonly_tool_concurrency)

        async def execute(index: int) -> ToolRound:
            """等待并发名额后再核验当前授权，超时包含工具内全部外调。"""
            call = message.tool_calls[index]
            async with semaphore:
                await self.verify(run)
                async with asyncio.timeout(run.pipeline_config.tool_timeout_seconds):
                    value = await self.tools.execute(run, model_call_id, call)
            return ToolRound(
                messages=[
                    ModelMessage(
                        role="tool",
                        tool_call_id=call.id,
                        content=json.dumps(value, ensure_ascii=False),
                    )
                ],
                finalize=any(item.get("status") == "DUPLICATE_CALL" for item in value),
            )

        result: list[ToolRound] = []
        index = 0
        while index < len(message.tool_calls):
            if message.tool_calls[index].name == "search_knowledge":
                result.append(await execute(index))
                index += 1
                continue
            end = index
            while (
                end < len(message.tool_calls) and message.tool_calls[end].name != "search_knowledge"
            ):
                end += 1
            batch = [asyncio.create_task(execute(position)) for position in range(index, end)]
            try:
                result.extend(await asyncio.gather(*batch))
            finally:
                for task in batch:
                    if not task.done():
                        task.cancel()
                await asyncio.gather(*batch, return_exceptions=True)
            index = end
        return ToolRound(
            messages=[message for group in result for message in group.messages],
            finalize=any(group.finalize for group in result),
        )

    async def hazard_report(self, run: runs.RunLease) -> bool:
        """危险信号只来自本次用户输入和有效人工事实，不能把助手安全提示当阳性报告。"""
        async with self.engine.connect() as db:
            statement = (
                await db.execute(
                    text(
                        "SELECT content FROM ai_message WHERE id=:id "
                        "AND conversation_id=:conversation "
                        "AND task_id=:task AND role='USER'"
                    ),
                    {
                        "id": run.input_message_id,
                        "conversation": run.conversation_id,
                        "task": run.task_id,
                    },
                )
            ).scalar_one()
            if reports_hazard(statement):
                return True
            for reference in run.context_manifest.facts:
                statement = (
                    await db.execute(
                        text(
                            "SELECT statement FROM ai_diagnostic_fact "
                            "WHERE id=:id AND task_id=:task "
                            "AND source_type IN ('USER_REPORT','HUMAN_RECORD')"
                        ),
                        {"id": reference.id, "task": run.task_id},
                    )
                ).scalar_one_or_none()
                if statement and reports_hazard(statement):
                    return True
        return False

    async def finish(
        self, run: runs.RunLease, result: RunResult, *, recovery_source_number: int | None = None
    ) -> None:
        """全部来源及原身份在提交前重核，随后一个事务提交回答和任务投影。"""
        await self.verify(run)
        actor = await self.rpc.identity.verify_ref(actor_ref(run))
        if recovery_source_number is None:
            await self.completion.commit(actor, run, result)
        else:
            await self.completion.commit(
                actor, run, result, recovery_source_number=recovery_source_number
            )
