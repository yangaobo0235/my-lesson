"""本人运行评价不共享聊天，失败运行同样支持版本化反馈。作者：yangaobo。"""

from sqlalchemy.ext.asyncio import AsyncEngine

from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import feedback as queries
from servicedesk_ai.repositories import runs as store
from servicedesk_ai.repositories.conversation_scope import scope
from servicedesk_ai.repositories.values import new_id
from servicedesk_ai.schemas.diagnosis import FeedbackInput
from servicedesk_ai.schemas.diagnosis_views import FeedbackView
from servicedesk_ai.services.conversations import ConversationService


class FeedbackService:
    """以会话拥有者、运行终态和反馈行版本维护唯一评价。"""

    def __init__(self, engine: AsyncEngine, conversations: ConversationService) -> None:
        """只复用会话授权；评价不需要读取回答或知识正文。"""
        self.engine, self.conversations = engine, conversations

    async def get(self, actor: VerifiedIdentity, run_id: str) -> FeedbackView | None:
        """当前有权的拥有者可查询原评价，不存在明确返回null。"""
        async with self.engine.connect() as db:
            run = await store.run(db, run_id)
        await self.conversations.authorized(actor, run.conversation_id)
        if run.owner_id != actor.actor.owner_id:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        async with self.engine.connect() as db:
            return await queries.read_feedback(db, run_id, actor.actor.owner_id)

    async def put(self, actor: VerifiedIdentity, run_id: str, form: FeedbackInput) -> FeedbackView:
        """同内容重试返回原结果；异义更新必须匹配当前版本，不能覆盖他人评价。"""
        async with self.engine.connect() as db:
            snapshot = await store.run(db, run_id)
        await self.conversations.authorized(actor, snapshot.conversation_id)
        async with self.engine.begin() as db:
            conversation = await scope(db, snapshot.conversation_id, lock=True)
            self.conversations.access.owner(actor, conversation)
            run = await store.run(db, run_id, lock=True)
            if run.owner_id != actor.actor.owner_id:
                raise BusinessError(CommonErrorCode.NOT_FOUND)
            if run.status not in {"SUCCEEDED", "FAILED", "INTERRUPTED", "CANCELLED"}:
                raise BusinessError(CommonErrorCode.CONFLICT)
            previous = await queries.read_feedback(db, run_id, actor.actor.owner_id, lock=True)
            content = form.model_dump(exclude={"row_version"})
            if previous is not None:
                if previous.model_dump(include=set(content)) == content:
                    return previous
                if form.row_version is None or previous.row_version != form.row_version:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                await queries.update_feedback(db, previous.id, int(previous.row_version), content)
            else:
                if form.row_version is not None:
                    raise BusinessError(CommonErrorCode.CONFLICT)
                await queries.insert_feedback(db, new_id(), run_id, actor.actor.owner_id, content)
            result = await queries.read_feedback(db, run_id, actor.actor.owner_id)
            if result is None:
                raise RuntimeError("已保存反馈缺少本事务回执")
            return result
