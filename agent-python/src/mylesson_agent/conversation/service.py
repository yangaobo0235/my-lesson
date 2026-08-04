from time import monotonic
from typing import Any
from uuid import UUID, uuid4

import structlog
from sqlalchemy.exc import IntegrityError

from mylesson_agent.container import Container
from mylesson_agent.conversation.lock import ConversationBusyError
from mylesson_agent.domain.api_models import AuthenticatedUser
from mylesson_agent.infrastructure.orm import AgentRunRow

log = structlog.get_logger(__name__)


class ConversationNotFoundError(RuntimeError):
    pass


class ConversationService:
    def __init__(self, container: Container) -> None:
        self._container = container

    async def submit(
        self,
        conversation_id: UUID,
        request_id: UUID,
        message: str,
        user: AuthenticatedUser,
    ) -> AgentRunRow:
        async with self._container.database.sessions() as session:
            conversation = await self._container.repository.owned(session, conversation_id, user.id)
            if conversation is None:
                raise ConversationNotFoundError
            existing = await self._container.repository.find_run_by_request(
                session, conversation_id, request_id
            )
            if existing is not None:
                return existing
            trace_id = str(uuid4())
            try:
                run = await self._container.repository.create_submission(
                    session,
                    conversation,
                    request_id,
                    message.strip(),
                    trace_id,
                    user.username,
                    user.roles,
                )
            except IntegrityError:
                await session.rollback()
                existing = await self._container.repository.find_run_by_request(
                    session, conversation_id, request_id
                )
                if existing is None:
                    raise
                return existing
        return run

    async def execute(self, run: AgentRunRow, question: str, user: AuthenticatedUser) -> None:
        started = monotonic()
        try:
            async with (
                self._container.conversation_lock.acquire(run.conversation_id),
                self._container.database.sessions() as session,
            ):
                await self._publish(session, run, "run_started", {"status": "RUNNING"})
                history = await self._container.repository.messages(
                    session, run.conversation_id, 12
                )
                context = "\n".join(
                    f"{message.role}: {message.content[:1000]}" for message in history[:-1]
                )
                await self._publish(session, run, "retrieval_started", {})
                state = await self._container.agent_runtime.run(
                    session=session,
                    question=question,
                    context=context,
                    user=user,
                    trace_id=run.trace_id,
                    request_id=run.request_id,
                )
                selection = state["selection"]
                await self._container.repository.update_route(
                    session,
                    run.id,
                    intent=selection.decision.intent.value,
                    profile_name=selection.profile.name,
                    profile_version=selection.profile.version,
                    conservative=selection.route.conservative,
                )
                await self._publish(
                    session,
                    run,
                    "intent_detected",
                    {
                        "intent": selection.decision.intent.value,
                        "confidence": selection.decision.confidence,
                        "reason": selection.decision.reason,
                    },
                )
                profile_data = {
                    "agentName": selection.profile.name,
                    "profileName": selection.profile.name,
                    "profileVersion": selection.profile.version,
                    "displayName": selection.profile.display_name,
                    "conservative": selection.route.conservative,
                }
                await self._publish(session, run, "agent_selected", profile_data)
                await self._publish(session, run, "agent_started", profile_data)
                retrieval = state["retrieval"]
                candidate_data = [
                    {
                        "chunkId": hit.chunk_id,
                        "title": hit.title,
                        "sourceUrl": hit.source_url,
                        "score": hit.score,
                    }
                    for hit in retrieval.hits
                ]
                await self._container.repository.save_retrieval_trace(
                    session,
                    run.id,
                    question,
                    candidate_data,
                    retrieval.reranked,
                )
                await self._publish(
                    session,
                    run,
                    "retrieval_completed",
                    {"hitCount": len(retrieval.hits), "reranked": retrieval.reranked},
                )
                tool_results = state.get("tool_results", [])
                for tool_result in tool_results:
                    await self._publish(
                        session,
                        run,
                        "tool_started",
                        {"toolName": tool_result.name},
                    )
                    await self._container.repository.save_tool_call(
                        session,
                        run.id,
                        tool_result.name,
                        tool_result.arguments,
                        {"data": tool_result.data, "error": tool_result.error},
                        tool_result.success,
                        tool_result.latency_ms,
                    )
                    await self._publish(
                        session,
                        run,
                        "tool_completed",
                        {
                            "toolName": tool_result.name,
                            "success": tool_result.success,
                            "status": "SUCCEEDED" if tool_result.success else "FAILED",
                        },
                    )
                answer = state["answer"]
                citations = [
                    citation.model_dump(by_alias=True) for citation in state.get("citations", [])
                ]
                latency_ms = int((monotonic() - started) * 1000)
                message = await self._container.repository.complete(
                    session,
                    run,
                    answer,
                    citations,
                    latency_ms,
                    len(tool_results),
                )
                for offset in range(0, len(answer), self._container.settings.answer_delta_size):
                    await self._publish(
                        session,
                        run,
                        "answer_delta",
                        {
                            "delta": answer[
                                offset : offset + self._container.settings.answer_delta_size
                            ]
                        },
                    )
                for citation in citations:
                    await self._publish(session, run, "citation", {"citation": citation})
                waiting = state.get("waiting_confirmation")
                if waiting:
                    await self._publish(
                        session,
                        run,
                        "workflow_waiting_confirmation",
                        {"draft": waiting},
                    )
                await self._publish(
                    session, run, "agent_completed", profile_data | {"success": True}
                )
                await self._publish(
                    session,
                    run,
                    "run_completed",
                    {
                        "status": "SUCCEEDED",
                        "assistantMessageId": str(message.id),
                        "latencyMillis": latency_ms,
                    },
                )
        except ConversationBusyError:
            async with self._container.database.sessions() as session:
                await self._container.repository.requeue(session, run.id)
        except Exception as exception:
            latency_ms = int((monotonic() - started) * 1000)
            log.exception("conversation_run_failed", run_id=str(run.id), error=str(exception))
            async with self._container.database.sessions() as session:
                await self._container.repository.fail(
                    session, run.id, exception.__class__.__name__, latency_ms
                )
                await self._publish(
                    session,
                    run,
                    "run_failed",
                    {
                        "status": "FAILED",
                        "code": "AI_CONVERSATION_FAILED",
                        "message": "对话处理失败，请稍后重试",
                        "retryable": True,
                    },
                )

    async def _publish(
        self,
        session: Any,
        run: AgentRunRow,
        event_type: str,
        data: dict[str, Any],
    ) -> None:
        await self._container.event_publisher.publish(
            session,
            event_type=event_type,
            conversation_id=run.conversation_id,
            run_id=run.id,
            request_id=run.request_id,
            trace_id=run.trace_id,
            data=data,
        )

    async def shutdown(self) -> None:
        return None
