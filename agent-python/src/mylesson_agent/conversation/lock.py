import asyncio
from collections.abc import AsyncIterator, Awaitable
from contextlib import asynccontextmanager, suppress
from typing import Any, cast
from uuid import UUID, uuid4

from redis.asyncio import Redis


class ConversationBusyError(RuntimeError):
    pass


class ConversationLock:
    def __init__(self, redis: Redis, ttl_seconds: int = 120) -> None:
        self._redis = redis
        self._ttl_seconds = ttl_seconds

    @asynccontextmanager
    async def acquire(self, conversation_id: UUID) -> AsyncIterator[None]:
        key = f"ml:agent:lock:{conversation_id}"
        token = str(uuid4())
        acquired = await self._redis.set(key, token, ex=self._ttl_seconds, nx=True)
        if not acquired:
            raise ConversationBusyError("Conversation already has an active run")
        renewal = asyncio.create_task(self._renew(key, token))
        try:
            yield
        finally:
            renewal.cancel()
            with suppress(asyncio.CancelledError):
                await renewal
            script = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """
            await cast(Awaitable[Any], self._redis.eval(script, 1, key, token))

    async def _renew(self, key: str, token: str) -> None:
        interval = max(self._ttl_seconds / 3, 1)
        script = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('expire', KEYS[1], ARGV[2])
        end
        return 0
        """
        while True:
            await asyncio.sleep(interval)
            renewed = await cast(
                Awaitable[Any],
                self._redis.eval(script, 1, key, token, str(self._ttl_seconds)),
            )
            if not renewed:
                return
