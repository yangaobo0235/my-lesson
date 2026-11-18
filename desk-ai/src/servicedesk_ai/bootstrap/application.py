"""API与内部RPC共用用例装配，连接仍归所属进程资源生命周期。作者：yangaobo。"""

from __future__ import annotations

from dataclasses import dataclass

from opentelemetry.trace import Tracer

from servicedesk_ai.bootstrap.resources import Resources
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc
from servicedesk_ai.infrastructure.index_replicas import IndexReplicas
from servicedesk_ai.infrastructure.run_quota import RunQuota
from servicedesk_ai.services.conversations import ConversationService
from servicedesk_ai.services.drafts import DraftService
from servicedesk_ai.services.evidence import EvidenceService
from servicedesk_ai.services.feedback import FeedbackService
from servicedesk_ai.services.indexing import IndexService
from servicedesk_ai.services.run_reads import RunReads
from servicedesk_ai.services.runs import RunService
from servicedesk_ai.services.task_access import TaskAccess
from servicedesk_ai.services.task_reads import TaskReads
from servicedesk_ai.services.tasks import TaskService


@dataclass(frozen=True)
class ApplicationServices:
    """无请求可变状态的用例集合；Web、RPC与worker使用同一规则。"""

    rpc: DomainRpc
    conversations: ConversationService
    drafts: DraftService
    feedback: FeedbackService
    evidence: EvidenceService
    index: IndexService
    replicas: IndexReplicas
    tracer: Tracer
    tasks: TaskService
    runs: RunService
    run_reads: RunReads
    task_reads: TaskReads


def services(resources: Resources, rpc: DomainRpc, settings: Settings) -> ApplicationServices:
    """只装配真实消费者，不在构造时运行模型、迁移或长任务。"""
    evidence = EvidenceService(resources.database, rpc)
    access = TaskAccess(resources.database, rpc)
    conversations = ConversationService(resources.database, access)
    runs = RunService(
        resources.database, access, RunQuota(resources.redis, settings.redis), settings
    )
    run_reads = RunReads(resources.database, runs, conversations, evidence)
    tasks = TaskService(
        resources.database,
        access,
        settings.task_budget,
        evidence,
    )
    replicas = IndexReplicas(
        resources.elasticsearch,
        resources.qdrant,
        settings.elasticsearch.index,
        settings.qdrant.collection,
        settings.model,
    )
    return ApplicationServices(
        rpc,
        conversations,
        DraftService(resources.database, access, run_reads),
        FeedbackService(resources.database, conversations),
        evidence,
        IndexService(resources.database),
        replicas,
        resources.tracer_provider.get_tracer("servicedesk_ai.rpc"),
        tasks,
        runs,
        run_reads,
        TaskReads(resources.database, access, conversations, run_reads),
    )
