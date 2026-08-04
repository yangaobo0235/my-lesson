import asyncio
from typing import Any
from uuid import uuid4

from mylesson_agent.conversation.lock import ConversationLock


class FakeRedis:
    def __init__(self) -> None:
        self.value: str | None = None
        self.renewals = 0

    async def set(self, _key: str, value: str, **_kwargs: Any) -> bool:
        self.value = value
        return True

    async def eval(self, script: str, _count: int, _key: str, token: str, *_args: Any) -> int:
        if self.value != token:
            return 0
        if "expire" in script:
            self.renewals += 1
        else:
            self.value = None
        return 1


async def test_conversation_lock_renews_and_releases_owned_token() -> None:
    redis = FakeRedis()
    lock = ConversationLock(redis, ttl_seconds=1)  # type: ignore[arg-type]
    async with lock.acquire(uuid4()):
        await asyncio.sleep(1.1)
        assert redis.renewals >= 1
    assert redis.value is None


async def test_conversation_lock_does_not_release_replaced_token() -> None:
    redis = FakeRedis()
    lock = ConversationLock(redis, ttl_seconds=30)  # type: ignore[arg-type]
    async with lock.acquire(uuid4()):
        redis.value = "new-owner"
    assert redis.value == "new-owner"
