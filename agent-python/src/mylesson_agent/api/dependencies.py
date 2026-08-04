from collections.abc import AsyncIterator
from functools import lru_cache

from sqlalchemy.ext.asyncio import AsyncSession

from mylesson_agent.container import Container, get_container
from mylesson_agent.conversation.service import ConversationService


async def database_session() -> AsyncIterator[AsyncSession]:
    container = get_container()
    async with container.database.sessions() as session:
        yield session


@lru_cache
def conversation_service() -> ConversationService:
    return ConversationService(get_container())


def container() -> Container:
    return get_container()
