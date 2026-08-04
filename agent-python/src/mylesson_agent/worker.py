import asyncio
from contextlib import suppress
from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

import structlog

from mylesson_agent.config import get_settings
from mylesson_agent.container import get_container
from mylesson_agent.conversation.service import ConversationService
from mylesson_agent.domain.api_models import AuthenticatedUser
from mylesson_agent.observability.setup import configure_logging, configure_tracing

log = structlog.get_logger(__name__)


async def run_worker() -> None:
    settings = get_settings()
    configure_logging(settings)
    configure_tracing(settings)
    container = get_container()
    service = ConversationService(container)
    worker_id = str(uuid4())

    async def heartbeat(run_id: UUID) -> None:
        while True:
            await asyncio.sleep(settings.worker_heartbeat_seconds)
            async with container.database.sessions() as session:
                active = await container.repository.heartbeat(session, run_id, worker_id)
            if not active:
                return

    try:
        while True:
            async with container.database.sessions() as session:
                recovered = await container.repository.recover_stale(
                    session,
                    datetime.now(UTC) - timedelta(seconds=settings.worker_stale_seconds),
                )
                claimed = await container.repository.claim_next(session, worker_id)
            if recovered:
                log.warning("stale_runs_requeued", count=recovered)
            if claimed is None:
                await asyncio.sleep(settings.worker_poll_seconds)
                continue
            run, message = claimed
            user = AuthenticatedUser(
                id=run.user_id,
                username=run.username,
                roles=run.user_roles,
                delegation_token="",
            )
            heartbeat_task = asyncio.create_task(heartbeat(run.id))
            try:
                await service.execute(run, message.content, user)
            finally:
                heartbeat_task.cancel()
                with suppress(asyncio.CancelledError):
                    await heartbeat_task
    finally:
        await container.close()


def main() -> None:
    asyncio.run(run_worker())


if __name__ == "__main__":
    main()
