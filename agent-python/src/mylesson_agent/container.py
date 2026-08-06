from functools import lru_cache

from redis.asyncio import Redis

from mylesson_agent.agents.routing import IntentRouter
from mylesson_agent.agents.runtime import AgentRuntime
from mylesson_agent.config import Settings, get_settings
from mylesson_agent.conversation.events import EventPublisher
from mylesson_agent.conversation.lock import ConversationLock
from mylesson_agent.conversation.repository import ConversationRepository
from mylesson_agent.infrastructure.database import Database
from mylesson_agent.knowledge.service import KnowledgeIndexer
from mylesson_agent.llm.client import ModelClient
from mylesson_agent.rag.search_client import JavaKnowledgeSearchClient
from mylesson_agent.rag.service import HybridRetriever
from mylesson_agent.tools.client import BusinessToolClient


class Container:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.database = Database(settings)
        self.redis = Redis.from_url(settings.redis_url, decode_responses=False)
        self.repository = ConversationRepository()
        self.event_publisher = EventPublisher(self.redis, self.repository, self.database)
        self.conversation_lock = ConversationLock(
            self.redis, ttl_seconds=settings.conversation_lock_ttl_seconds
        )
        self.model = ModelClient(settings)
        self.keyword_search = JavaKnowledgeSearchClient(settings)
        self.knowledge_indexer = KnowledgeIndexer(self.model, self.keyword_search)
        self.tool_client = BusinessToolClient(settings)
        self.router = IntentRouter(self.model, settings.intent_confidence_threshold)
        self.retriever = HybridRetriever(settings, self.model, self.keyword_search)
        self.agent_runtime = AgentRuntime(
            self.router,
            self.retriever,
            self.tool_client,
            self.model,
        )

    async def close(self) -> None:
        await self.tool_client.close()
        await self.keyword_search.close()
        await self.model.close()
        await self.redis.aclose()
        await self.database.close()


@lru_cache
def get_container() -> Container:
    return Container(get_settings())
