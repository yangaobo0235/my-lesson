"""Run最终原子提交：可见消息、检查实例、假设、任务事件与终态一并落库。作者：yangaobo。"""

from __future__ import annotations

import hashlib
import json
from dataclasses import replace

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine

from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import evidence as evidence_queries
from servicedesk_ai.repositories import runs, tasks
from servicedesk_ai.repositories.conversation_scope import scope
from servicedesk_ai.repositories.values import new_id
from servicedesk_ai.schemas.procedures import ProcedureCheck
from servicedesk_ai.schemas.runs import (
    CheckReference,
    ProcedureChecks,
    ProcedureClarification,
    ProcedureConclusion,
    ProcedureResult,
    RunResult,
)
from servicedesk_ai.schemas.tasks import (
    AnswerSchema,
    CheckDependencies,
    CheckProposal,
    Hypothesis,
    HypothesisState,
    NormalizedAnswer,
    TaskEnvironment,
    TaskEventDetails,
    TaskReceipt,
    TaskStatus,
)
from servicedesk_ai.services.evidence import Evidence, complete_knowledge_numbers
from servicedesk_ai.services.task_access import TaskAccess
from servicedesk_ai.services.tasks import TERMINAL_STATUSES

RECHECK_STATUSES = {"NEEDS_RECHECK", "EXPIRED", "FAILED", "SKIPPED", "WITHDRAWN"}


def missing_procedure_fields(evidence: list[Evidence], context: TaskEnvironment | None) -> set[str]:
    """模型可问与提交可问共用同一审核规程环境缺口，不能把观察检查改为自由澄清。"""
    required: set[str] = set()
    for source in evidence:
        if source.source_type != "PROCEDURE_CHECK" or source.usage_kind != "CONTENT":
            continue
        snapshot = source.source_version.get("procedure")
        if not isinstance(snapshot, dict):
            raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
        check = ProcedureCheck.model_validate(snapshot["check"])
        required.update(snapshot["required_fields"])
        required.update(check.required_fields)
    return {
        field
        for field in required
        if context is None or getattr(context, field) in {None, "UNKNOWN"}
    }


def restoration_source_numbers(evidence: list[Evidence], context_version: int | None) -> set[int]:
    """枚举已输入的当前环境恢复反馈，只约束模型选择，不替代提交时的数据库复核。"""
    numbers = set()
    for source in evidence:
        if (
            source.source_type != "MESSAGE"
            or source.usage_kind != "CONTENT"
            or not source.source_version.get("check_id")
        ):
            continue
        payload = json.loads(source.excerpt)
        answer = NormalizedAnswer.model_validate(payload["answer"]).answer
        if (
            payload["context_version"] == context_version
            and answer.execution_status == "PERFORMED"
            and answer.outcome == "RESTORED_REPORTED"
        ):
            numbers.add(source.number)
    return numbers


class ModelOutputRejected(BusinessError):
    """模型提议违反可纠正的输出合同；事务回滚后允许一次有预算的纠正。"""

    def __init__(self, reason: str) -> None:
        """原因仅由宿主固定文本提供，不拼接外部正文或供应商异常。"""
        super().__init__(CommonErrorCode.INVALID_REQUEST)
        self.reason = reason


def bind_procedure_dependencies(proposal: CheckProposal, snapshot: dict) -> CheckProposal:
    """宿主强制合并审核定义的环境依赖，模型可追加但不能删掉失效条件。"""
    check = ProcedureCheck.model_validate(snapshot["check"])
    required = set(snapshot["required_fields"]) | set(check.required_fields)
    return CheckProposal.model_validate(
        proposal.model_dump()
        | {"dependency_fields": sorted(set(proposal.dependency_fields) | required)}
    )


def expand_procedure_result(result: ProcedureResult, evidence: list[Evidence]) -> RunResult:
    """从本轮完整审核来源组装检查，不让模型重写指令、选项或文档身份。

    此处只转换公开提议；复查身份由任务状态绑定，角色、环境、原修订、前置答案及重复检查仍在最终事务校验，
    不产生任何正式操作或已执行事实。
    """
    sources = {
        item.number: item
        for item in evidence
        if item.source_type == "PROCEDURE_CHECK" and item.usage_kind == "CONTENT"
    }
    checks = []
    citations = list(result.citation_numbers)
    action = result.next_action
    for selection in action.selections if isinstance(action, ProcedureChecks) else []:
        source = sources.get(selection.source_number)
        if source is None:
            raise ModelOutputRejected("CHECKS只能选择本轮完整load_procedure来源号")
        snapshot = source.source_version.get("procedure")
        if not isinstance(snapshot, dict) or not source.procedure_document_id:
            raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
        check = ProcedureCheck.model_validate(snapshot["check"])
        if source.procedure_check_key != check.check_key:
            raise BusinessError(CommonErrorCode.CONFLICT)
        proposal = CheckProposal(
            check_key=check.check_key,
            check_kind="CHECK",
            instruction=check.instruction,
            answer_schema=check.answer_schema,
            dependency_fields=check.required_fields,
            procedure_document_id=source.procedure_document_id,
            procedure_check_key=check.check_key,
            source_numbers=[source.number],
        )
        checks.append(bind_procedure_dependencies(proposal, snapshot))
        if source.number not in citations:
            citations.append(source.number)
    if isinstance(action, ProcedureClarification):
        question = action
        checks.append(
            CheckProposal(
                check_key=question.check_key,
                check_kind="CLARIFICATION",
                instruction=question.question,
                answer_schema=AnswerSchema(kind="TEXT"),
                dependency_fields=question.dependency_fields,
                previous_check_id=question.previous_check_id,
            )
        )
    if isinstance(action, ProcedureChecks):
        explanation = "请按下面的检查说明操作，并反馈实际观察；完成操作不代表原问题已经恢复。"
    elif isinstance(action, ProcedureClarification):
        explanation = "继续排查前，需要确认以下信息；不确定的内容请如实说明。"
    elif action.answer_type == "ANSWERED":
        explanation = "根据您提交的复测反馈，原问题已恢复。这是您的反馈记录，尚未经现场实测核验。"
        if action.feedback_source_number is None:
            raise ModelOutputRejected("恢复结论缺少人工复测反馈来源")
        if action.feedback_source_number not in citations:
            citations.append(action.feedback_source_number)
    elif action.answer_type == "HANDOVER":
        explanation = "本轮自动排查已停止，请联系支持人员结合现有资料和反馈继续处理。"
    else:
        explanation = "目前证据不足以继续判断，请补充相关资料或联系支持人员核对。"
    return RunResult.model_validate(
        result.model_dump(exclude={"next_action"})
        | {
            "text": explanation,
            "checks": checks,
            "citation_numbers": citations,
            "answer_type": "ANSWERED"
            if isinstance(action, ProcedureChecks)
            else "CLARIFICATION"
            if isinstance(action, ProcedureClarification)
            else action.answer_type,
        }
    )


class RunCompletion:
    """调用方须先重核原身份及全部实际来源；本地提交再次检查状态、版本和执行权。"""

    def __init__(self, engine: AsyncEngine, access: TaskAccess) -> None:
        """与任务人工用例共用数据库和授权边界，不执行外部业务写入。"""
        self.engine, self.access = engine, access

    async def validate_recovery_feedback(
        self, db: AsyncConnection, run: runs.RunLease, source_number: int
    ) -> None:
        """恢复说明只绑定当前环境最新检查反馈；提交锁内重核，旧恢复或未知结果不能冒充现状。"""
        if run.task_id is None:
            raise ModelOutputRejected("恢复结论必须属于诊断任务")
        row = (
            (
                await db.execute(
                    text("""SELECT c.id,c.row_version,c.normalized_answer,s.source_version
                    FROM ai_source_usage s
                    JOIN ai_message m ON m.id=s.message_id AND m.role='USER'
                    JOIN ai_diagnostic_check c ON c.answer_message_id=m.id AND c.task_id=m.task_id
                    WHERE s.run_id=:run AND s.source_number=:number AND s.source_type='MESSAGE'
                      AND s.usage_kind='CONTENT' AND c.task_id=:task AND c.check_kind='CHECK'
                      AND c.status='ANSWERED' AND c.context_version=:context
                      AND c.id=(SELECT latest.id FROM ai_diagnostic_check latest
                        WHERE latest.task_id=:task AND latest.context_version=:context
                          AND latest.check_kind='CHECK' AND latest.status='ANSWERED'
                        ORDER BY latest.answered_at DESC,latest.id DESC LIMIT 1)"""),
                    {
                        "run": run.id,
                        "number": source_number,
                        "task": run.task_id,
                        "context": run.diagnosis_context_version,
                    },
                )
            )
            .mappings()
            .first()
        )
        if row is None:
            raise ModelOutputRejected("该反馈不是当前环境最新的有效检查结果，不能据此确认恢复")
        version = json.loads(row["source_version"])
        answer = NormalizedAnswer.model_validate_json(row["normalized_answer"]).answer
        if (
            version.get("check_id") != row["id"]
            or version.get("row_version") != row["row_version"]
            or answer.execution_status != "PERFORMED"
            or answer.outcome != "RESTORED_REPORTED"
            or await tasks.reported_conditions(db, run.task_id)
        ):
            raise ModelOutputRejected(
                "当前反馈未确认复测恢复、已经失效或仍有风险报告，不能确认恢复"
            )

    async def prepare_procedure_result(
        self, run: runs.RunLease, result: ProcedureResult, evidence: list[Evidence]
    ) -> RunResult:
        """选择审核检查即提出该检查；宿主绑定同键最新可复查实例，不能重做已答步骤。

        这里只读并准备提议。提交时仍锁任务、比较输入版本并重核前驱；未知或迟到结果
        不能据此绕过原子提交，也不产生已执行事实。
        """
        if isinstance(result.next_action, ProcedureClarification):
            missing = missing_procedure_fields(evidence, run.context_manifest.support_context)
            keys = {
                snapshot["check"]["check_key"]
                for source in evidence
                if source.source_type == "PROCEDURE_CHECK"
                and source.usage_kind == "CONTENT"
                and isinstance(snapshot := source.source_version["procedure"], dict)
            }
            question = result.next_action
            if (
                not question.dependency_fields
                or not set(question.dependency_fields).issubset(missing)
                or question.check_key in keys
            ):
                raise ModelOutputRejected(
                    "CLARIFY只补问已读规程的未知必需环境，不能改写审核检查或复用其键；"
                    "观察反馈必须用CHECKS选择已读检查来源号"
                )
        expanded = expand_procedure_result(result, evidence)
        if isinstance(result.next_action, ProcedureConclusion):
            number = result.next_action.feedback_source_number
            if number is not None:
                if number not in restoration_source_numbers(
                    evidence, run.diagnosis_context_version
                ):
                    raise ModelOutputRejected(
                        "恢复结论只能引用本轮已读、当前环境的人工复测恢复反馈"
                    )
                async with self.engine.connect() as db:
                    await self.validate_recovery_feedback(db, run, number)
        if not expanded.checks:
            return expanded
        if run.task_id is None:
            raise ModelOutputRejected("审核检查必须属于诊断任务")
        checks = []
        async with self.engine.connect() as db:
            for proposal in expanded.checks:
                if proposal.procedure_document_id:
                    proposal = await self.bind_previous_check(db, run.task_id, proposal)
                checks.append(proposal)
        return expanded.model_copy(update={"checks": checks})

    async def bind_previous_check(
        self, db: AsyncConnection, task_id: str, proposal: CheckProposal
    ) -> CheckProposal:
        """候选筛选与提议展开共用最新实例规则；提交仍在锁内重核前驱。"""
        previous = await tasks.latest_check(db, task_id, proposal.check_key)
        if previous and previous["status"] not in RECHECK_STATUSES:
            raise ModelOutputRejected("该审核检查已回答或仍待答，应沿现有反馈继续")
        return proposal.model_copy(
            update={"previous_check_id": previous["id"] if previous else None}
        )

    async def has_diagnostic_basis(self, run: runs.RunLease, evidence: list[Evidence]) -> bool:
        """只凭实际完整资料、工具记录或当前检查反馈进入生成，不以提问和预览充当依据。

        这是自由正文生成的最低条件，不授予操作资格；检查的完整章节、审核前提及来源
        授权仍在最终提交重核。咨询不走此门槛，人工反馈也不代表设备已实测恢复。
        """
        content = [item for item in evidence if item.usage_kind == "CONTENT"]
        if any(
            item.source_type in {"PROCEDURE_CHECK", "TICKET_EVENT"}
            or (
                item.source_type == "ATTACHMENT"
                and item.source_version.get("content_complete") is True
            )
            for item in content
        ):
            return True
        if complete_knowledge_numbers(
            [
                {
                    "source_type": item.source_type,
                    "usage_kind": item.usage_kind,
                    "source_number": item.number,
                    "source_version": json.dumps(item.source_version),
                    "chunk_id": item.chunk_id,
                }
                for item in content
            ]
        ):
            return True
        references = {item.id: item for item in run.context_manifest.checks}
        for item in content:
            if item.source_type == "MESSAGE" and "check_id" in item.source_version:
                reference = CheckReference.model_validate(
                    {
                        "id": item.source_version["check_id"],
                        "row_version": item.source_version["row_version"],
                        "context_version": run.diagnosis_context_version,
                    }
                )
                if reference.id in references and references[reference.id] != reference:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                references[reference.id] = reference
        if not references:
            return False
        if run.task_id is None:
            raise BusinessError(CommonErrorCode.CONFLICT)
        async with self.engine.connect() as db:
            for reference in references.values():
                check = await tasks.check(db, run.task_id, reference.id)
                if check.row_version != reference.row_version:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                if (
                    check.context_version
                    == reference.context_version
                    == run.diagnosis_context_version
                    and check.check_kind == "CHECK"
                    and check.status == "ANSWERED"
                    and check.normalized_answer is not None
                ):
                    return True
        return False

    async def unavailable_procedure_checks(
        self, run: runs.RunLease, evidence: list[Evidence]
    ) -> dict[int, str]:
        """复用提交规则筛除当前不可操作项，保留完整证据及固定拒绝原因。

        此结果仅约束本次模型选择，不授予执行权。最终提交仍锁定任务并重核状态、
        环境、权限与前置答案；读取失败或状态变化不转换成空候选继续生成。
        """
        if run.task_id is None:
            raise BusinessError(CommonErrorCode.CONFLICT)
        blocked: dict[int, str] = {}
        async with self.engine.connect() as db:
            task = await tasks.task(db, run.task_id)
            if (
                task.status in TERMINAL_STATUSES
                or task.task_state_version != run.input_state_version
                or task.diagnosis_context_version != run.diagnosis_context_version
                or await tasks.reported_conditions(db, run.task_id)
            ):
                raise BusinessError(CommonErrorCode.CONFLICT)
            for source in evidence:
                if source.source_type != "PROCEDURE_CHECK" or source.usage_kind != "CONTENT":
                    continue
                selected = ProcedureResult.model_validate(
                    {
                        "nextAction": {
                            "kind": "CHECKS",
                            "selections": [{"sourceNumber": source.number}],
                        },
                    }
                )
                proposal = expand_procedure_result(selected, [source]).checks[0]
                snapshot = source.source_version["procedure"]
                if not isinstance(snapshot, dict):
                    raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
                try:
                    proposal = await self.bind_previous_check(db, run.task_id, proposal)
                    await self.validate_procedure_check(db, run, task, proposal, snapshot)
                except ModelOutputRejected as error:
                    blocked[source.number] = error.reason
        return blocked

    async def commit(
        self,
        actor: VerifiedIdentity,
        run: runs.RunLease,
        result: RunResult,
        *,
        recovery_source_number: int | None = None,
    ) -> None:
        """失败或取消不遗留可答检查；等待用户时本轮成功结束并释放worker。"""
        context = None
        if run.task_id:
            async with self.engine.connect() as db:
                original = await tasks.task(db, run.task_id)
            _, context = await self.access.authorize(actor, original, new_execution=True)
        async with self.engine.begin() as db:
            conversation = await scope(db, run.conversation_id, lock=True)
            self.access.owner(actor, conversation)
            task = await tasks.task(db, run.task_id, lock=True) if run.task_id else None
            await runs.owned(db, run)
            if task and (
                task.status in TERMINAL_STATUSES
                or task.task_state_version != run.input_state_version
                or task.diagnosis_context_version != run.diagnosis_context_version
                or (
                    conversation.ticket_id
                    and (
                        context is None
                        or context.diagnosis_context_version != run.diagnosis_context_version
                    )
                )
            ):
                raise BusinessError(CommonErrorCode.CONFLICT)
            if task is None and (
                result.checks
                or result.hypotheses
                or result.citation_numbers
                or result.proposed_record
                or result.proposed_resolution
            ):
                raise ModelOutputRejected("普通咨询不能包含业务检查、假设、引用或动作草稿")
            if recovery_source_number is not None:
                if result.answer_type != "ANSWERED" or result.checks:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                await self.validate_recovery_feedback(db, run, recovery_source_number)
            if (
                bool(result.proposed_resolution) != bool(result.resolution_type)
                or (result.resolution_type == "REFERRED" and not result.referral_target)
                or (result.resolution_type != "REFERRED" and result.referral_target)
            ):
                raise ModelOutputRejected(
                    "解决建议正文与resolutionType须同时提供；REFERRED必须提供referralTarget。"
                    "只有text中的建议不能代替proposedResolution。"
                )
            sources = set(
                (
                    await db.execute(
                        text("""
                SELECT source_number FROM ai_source_usage WHERE run_id=:id AND usage_kind='CONTENT'
            """),
                        {"id": run.id},
                    )
                ).scalars()
            )
            referenced = set(result.citation_numbers)
            for proposal in result.checks:
                referenced.update(proposal.source_numbers)
            if not referenced.issubset(sources):
                raise ModelOutputRejected(
                    "只能引用本轮已实际输入的来源号，历史权限依赖不属于本轮正文"
                )
            used_sources = await evidence_queries.source_usages(db, {"id": run.id})
            complete_knowledge = complete_knowledge_numbers(used_sources)
            procedure_sources = (
                (
                    await db.execute(
                        text("""
                SELECT source_number,procedure_document_id,procedure_check_key,source_version
                FROM ai_source_usage WHERE run_id=:id AND source_type='PROCEDURE_CHECK'
                    AND usage_kind='CONTENT'
            """),
                        {"id": run.id},
                    )
                )
                .mappings()
                .all()
            )
            procedure_numbers = {item["source_number"] for item in procedure_sources}
            for proposal in result.checks:
                if proposal.check_kind == "CHECK" and not set(proposal.source_numbers).intersection(
                    complete_knowledge | procedure_numbers
                ):
                    # 召回预览或用户声称不能直接成为操作指引的完整依据。
                    raise ModelOutputRejected(
                        "操作检查缺少本轮完整知识或审核检查依据；取消无依据操作"
                    )
            if any(proposal.check_kind == "CHECK" for proposal in result.checks):
                if run.task_id and await tasks.reported_conditions(db, run.task_id):
                    raise BusinessError(CommonErrorCode.CONFLICT)
                used = await evidence_queries.source_usages(db, {"id": run.id})
                complete_chunks = {
                    row["chunk_id"] for row in used if row["source_number"] in complete_knowledge
                }
                for row in used:
                    if row["source_number"] not in complete_knowledge or not any(
                        row["source_number"] in proposal.source_numbers
                        for proposal in result.checks
                        if proposal.check_kind == "CHECK"
                    ):
                        continue
                    chunk = await evidence_queries.readable_chunk(db, {"id": row["chunk_id"]})
                    if chunk is None:
                        raise BusinessError(CommonErrorCode.CONFLICT)
                    section = chunk["parent_chunk_id"] or chunk["id"]
                    parts = await evidence_queries.adjacent_sections(db, section)
                    if parts and not {part["id"] for part in parts}.issubset(complete_chunks):
                        # 任何已用于指引的叶片都必须补读所在章节的全部分段。
                        raise ModelOutputRejected("操作检查引用的章节未全部读完，不能输出该操作")
            if task and result.answer_type == "ANSWERED" and not result.citation_numbers:
                raise ModelOutputRejected(
                    "业务ANSWERED须引用实际来源；无依据时如实表达不足或提出澄清"
                )
            output_version = None
            if task:
                if await tasks.open_check_count(db, task.id):
                    raise BusinessError(CommonErrorCode.CONFLICT)
                for index, proposal in enumerate(result.checks):
                    if proposal.procedure_document_id:
                        candidates = [
                            item
                            for item in procedure_sources
                            if item["source_number"] in proposal.source_numbers
                            and item["procedure_document_id"] == proposal.procedure_document_id
                            and item["procedure_check_key"] == proposal.procedure_check_key
                        ]
                        if len(candidates) != 1:
                            raise ModelOutputRejected(
                                "规程检查须引用本轮load_procedure返回的对应检查来源"
                            )
                        snapshot = json.loads(candidates[0]["source_version"])["procedure"]
                        proposal = bind_procedure_dependencies(proposal, snapshot)
                        result.checks[index] = proposal
                        await self.validate_procedure_check(db, run, task, proposal, snapshot)
                        if task.procedure_document_id is None:
                            await db.execute(
                                text("""UPDATE ai_diagnosis_task
                                SET procedure_document_id=:document,procedure_sha256=:sha
                                WHERE id=:id"""),
                                {
                                    "document": proposal.procedure_document_id,
                                    "sha": snapshot["definition_sha256"],
                                    "id": task.id,
                                },
                            )
                            task = replace(
                                task,
                                procedure_document_id=proposal.procedure_document_id,
                                procedure_sha256=snapshot["definition_sha256"],
                            )
                    elif proposal.check_kind == "CHECK" and (
                        task.procedure_document_id
                        or set(proposal.source_numbers) & procedure_numbers
                    ):
                        raise ModelOutputRejected("已绑定规程的操作检查不能省略审核规程身份")
                changed = [
                    await self.create_check(db, run, task, proposal) for proposal in result.checks
                ]
                hypotheses = await self.hypotheses(db, run, task, result)
                status: TaskStatus = "WAITING_USER" if changed else "ACTIVE"
                output_version = task.task_state_version + 1
                receipt = TaskReceipt(
                    task_id=task.id,
                    event_id=new_id(),
                    task_state_version=str(output_version),
                    status=status,
                )
                details = TaskEventDetails(
                    receipt=receipt,
                    reason="本轮已完成，等待明确反馈或继续操作",
                    changed_check_ids=changed,
                    hypotheses_before=task.hypothesis_state,
                    hypotheses_after=hypotheses,
                )
                await db.execute(
                    text("""
                    INSERT INTO ai_task_event(id,task_id,state_version,
                                              actor_type,event_type,details)
                    VALUES (:id,:task,:version,'WORKER','RUN_COMPLETED',:details)
                """),
                    {
                        "id": receipt.event_id,
                        "task": task.id,
                        "version": output_version,
                        "details": details.model_dump_json(),
                    },
                )
                await tasks.advance(
                    db,
                    task,
                    status=status,
                    hypotheses=hypotheses,
                    context=task.support_context,
                    context_version=task.diagnosis_context_version,
                )
            message_id = new_id()
            await db.execute(
                text("""
                UPDATE ai_conversation SET message_sequence=message_sequence+1,
                    updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """),
                {"id": run.conversation_id},
            )
            await db.execute(
                text("""
                INSERT INTO ai_message(id,conversation_id,task_id,sequence_number,
                                       role,content,content_sha256)
                SELECT :id,id,:task,message_sequence,'ASSISTANT',:content,:sha
                FROM ai_conversation WHERE id=:conversation
            """),
                {
                    "id": message_id,
                    "task": run.task_id,
                    "content": result.text,
                    "sha": hashlib.sha256(result.text.encode()).hexdigest(),
                    "conversation": run.conversation_id,
                },
            )
            await db.execute(
                text("""
                UPDATE ai_run SET status='SUCCEEDED',output_message_id=:message,
                    output_state_version=:version,structured_result=:result,answer_type=:type,
                    execution_phase='COMMIT',claim_token=NULL,claim_until=NULL,
                    finished_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=:id
            """),
                {
                    "id": run.id,
                    "message": message_id,
                    "version": output_version,
                    "result": result.model_dump_json(),
                    "type": result.answer_type,
                },
            )

    async def create_check(
        self,
        db: AsyncConnection,
        run: runs.RunLease,
        task: tasks.DiagnosisTask,
        proposal: CheckProposal,
    ) -> str:
        """检查键不是执行脚本；同键复查必须明确前驱，原答案和环境不得被覆盖。"""
        previous = await tasks.latest_check(db, task.id, proposal.check_key)
        if previous:
            if (
                previous["id"] != proposal.previous_check_id
                or previous["status"] not in RECHECK_STATUSES
            ):
                raise ModelOutputRejected(
                    "该检查已经回答或没有有效复查前驱，不能重复提出；应沿已有反馈继续"
                )
        elif proposal.previous_check_id:
            raise ModelOutputRejected("复查前驱不存在，不能虚构previousCheckId")
        if (
            proposal.procedure_document_id
            and proposal.procedure_document_id != task.procedure_document_id
        ):
            raise BusinessError(CommonErrorCode.CONFLICT)
        identifier = new_id()
        await db.execute(
            text("""
            INSERT INTO ai_diagnostic_check(id,task_id,proposed_run_id,check_key,check_kind,
                attempt_number,procedure_document_id,procedure_check_key,instruction,answer_schema,
                executor_type,requested_actor_id,context_version,dependency_fields,
                previous_check_id,expires_at)
            VALUES (:id,:task,:run,:key,:kind,:attempt,:document,:procedure_key,
                :instruction,:schema,
                :executor,:actor,:context,:dependencies,:previous,UTC_TIMESTAMP(6)+INTERVAL 7 DAY)
        """),
            {
                "id": identifier,
                "task": task.id,
                "run": run.id,
                "key": proposal.check_key,
                "kind": proposal.check_kind,
                "attempt": previous["attempt_number"] + 1 if previous else 1,
                "document": proposal.procedure_document_id,
                "procedure_key": proposal.procedure_check_key,
                "instruction": proposal.instruction,
                "schema": proposal.answer_schema.model_dump_json(),
                "executor": "CUSTOMER"
                if run.context_manifest.mode == "CUSTOMER_SELF_SERVICE"
                else "TECHNICIAN",
                "actor": run.owner_id,
                "context": task.diagnosis_context_version,
                "dependencies": CheckDependencies(
                    items=proposal.dependency_fields
                ).model_dump_json(),
                "previous": proposal.previous_check_id,
            },
        )
        return identifier

    async def validate_procedure_check(
        self,
        db: AsyncConnection,
        run: runs.RunLease,
        task: tasks.DiagnosisTask,
        proposal: CheckProposal,
        snapshot: dict,
    ) -> None:
        """核对人工定义、角色、环境和已实际执行前提，模型不能改写已审核指引。"""
        check = ProcedureCheck.model_validate(snapshot["check"])
        role = "CUSTOMER" if run.context_manifest.mode == "CUSTOMER_SELF_SERVICE" else "TECHNICIAN"
        required = set(check.required_fields) | set(snapshot["required_fields"])
        if proposal.check_kind != "CHECK":
            raise ModelOutputRejected(
                "审核规程检查的checkKind必须为CHECK；CLARIFICATION只用于补充资料，"
                "不能替代规程观察。保留该检查的正确来源号"
            )
        if role not in check.roles or check.risk_level != "LOW":
            raise ModelOutputRejected("该规程检查不允许当前角色执行或需要人工交接；移除操作并交接")
        if proposal.check_key != check.check_key:
            raise ModelOutputRejected("checkKey必须与已读审核检查的check_key一致，不能另造身份")
        if proposal.instruction != check.instruction:
            raise ModelOutputRejected("instruction必须原样复制已读审核检查的instruction，不能改写")
        if proposal.answer_schema != check.answer_schema:
            raise ModelOutputRejected("answerSchema必须与已读审核检查的answer_schema完全一致")
        if not required.issubset(proposal.dependency_fields):
            raise BusinessError(CommonErrorCode.CONFLICT)
        if any(getattr(task.support_context, field) in {None, "UNKNOWN"} for field in required):
            raise ModelOutputRejected(
                "当前检查的必需环境尚未知；移除操作并仅澄清required_fields缺失项"
            )
        if task.procedure_document_id is not None:
            if task.procedure_document_id != proposal.procedure_document_id:
                raise ModelOutputRejected("任务已绑定其他规程，不能静默切换procedureDocumentId")
            if task.procedure_sha256 != snapshot["definition_sha256"]:
                raise BusinessError(CommonErrorCode.CONFLICT)
        allowed_answers = {
            condition.check_key: set(condition.allowed_values)
            for condition in check.prerequisite_answers
        }
        for key in check.prerequisite_check_keys:
            prior = (
                (
                    await db.execute(
                        text("""
                SELECT status,context_version,normalized_answer FROM ai_diagnostic_check
                WHERE task_id=:task AND procedure_document_id=:document AND procedure_check_key=:key
                ORDER BY created_at DESC,id DESC LIMIT 1
            """),
                        {"task": task.id, "document": proposal.procedure_document_id, "key": key},
                    )
                )
                .mappings()
                .first()
            )
            if prior is None or prior["context_version"] != task.diagnosis_context_version:
                raise ModelOutputRejected("前置检查尚未完成或已因环境变化失效；不能跳过前置操作")
            answer = json.loads(prior["normalized_answer"]) if prior["normalized_answer"] else {}
            if prior["status"] != "OBSERVED_DONE" and (
                prior["status"] != "ANSWERED"
                or answer.get("answer", {}).get("execution_status") != "PERFORMED"
            ):
                raise ModelOutputRejected(
                    "前置检查未实际执行；回答过不等于PERFORMED，不能继续后续操作"
                )
            if (
                key in allowed_answers
                and answer.get("answer", {}).get("value") not in allowed_answers[key]
            ):
                raise ModelOutputRejected(
                    "前置检查虽然执行过，但实际答案不满足审核规程的允许值；"
                    "不得提出该操作，应按现有反馈交接或说明证据不足"
                )

    async def hypotheses(
        self, db: AsyncConnection, run: runs.RunLease, task: tasks.DiagnosisTask, result: RunResult
    ) -> HypothesisState:
        """假设只进入有界投影，支持和反证必须是本任务当前环境仍有效的真实事实。"""
        current = {item.hypothesis_key: item for item in task.hypothesis_state.items}
        supplied_facts = (
            set(
                (
                    await db.execute(
                        text("""SELECT fact_id FROM ai_source_usage WHERE run_id=:run
                    AND source_type='FACT' AND usage_kind='CONTENT'"""),
                        {"run": run.id},
                    )
                ).scalars()
            )
            if result.hypotheses
            else set()
        )
        for suggestion in result.hypotheses:
            item = Hypothesis(
                **suggestion.model_dump(),
                context_version=task.diagnosis_context_version,
                proposed_run_id=run.id,
            )
            for identifier in item.supporting_fact_ids + item.opposing_fact_ids:
                if identifier not in supplied_facts:
                    raise ModelOutputRejected(
                        "假设只能使用本轮已读取的factId；用户文本中出现的标识或历史元信息不证明已读事实"
                    )
                await tasks.active_fact(db, task.id, identifier)
                version = (
                    await db.execute(
                        text("""
                    SELECT environment_snapshot->'$.diagnosis_context_version'
                    FROM ai_diagnostic_fact WHERE id=:id AND task_id=:task
                """),
                        {"id": identifier, "task": task.id},
                    )
                ).scalar_one()
                if int(version) != task.diagnosis_context_version:
                    raise BusinessError(CommonErrorCode.CONFLICT)
            current[item.hypothesis_key] = item
        return HypothesisState(items=list(current.values()))
