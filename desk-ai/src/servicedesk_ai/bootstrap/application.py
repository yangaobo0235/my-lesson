"""API与内部RPC共用用例装配，连接仍归所属进程资源生命周期。作者：yangaobo。"""

from __future__ import annotations

from dataclasses import dataclass

from opentelemetry.trace import Tracer

from servicedesk_ai.bootstrap.resources import Resources
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc
from servicedesk_ai.infrastructure.index_replicas import IndexReplicas
from servicedesk_ai.services.conversations import ConversationService
from servicedesk_ai.services.drafts import DraftService
from servicedesk_ai.services.evidence import EvidenceService
from servicedesk_ai.services.feedback import FeedbackService
from servicedesk_ai.services.indexing import IndexService


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


def services(resources: Resources, rpc: DomainRpc, settings: Settings) -> ApplicationServices:
    """只装配真实消费者，不在构造时运行模型、迁移或长任务。"""
    evidence = EvidenceService(resources.database, rpc)
    conversations = ConversationService(
        resources.database, resources.redis, rpc, settings, evidence
    )
    replicas = IndexReplicas(
        resources.elasticsearch,
        resources.qdrant,
        resources.model,
        settings.elasticsearch.index,
        settings.qdrant.collection,
        settings.model,
    )
    return ApplicationServices(
        rpc,
        conversations,
        DraftService(resources.database, conversations),
        FeedbackService(resources.database, conversations),
        evidence,
        IndexService(resources.database),
        replicas,
        resources.tracer_provider.get_tracer("servicedesk_ai.rpc"),
    )
