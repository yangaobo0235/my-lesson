"""本人反馈与显式共享，技术私有会话反馈不进入知识管理员待办。作者：yangaobo。"""

from __future__ import annotations

from collections.abc import Mapping
from datetime import UTC

from sqlalchemy.engine import RowMapping
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine

from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.repositories import feedback as queries
from servicedesk_ai.repositories.diagnosis import new_id
from servicedesk_ai.schemas.diagnosis import FeedbackHandle, FeedbackInput, FeedbackShare
from servicedesk_ai.schemas.diagnosis_views import (
    FeedbackQueueItem,
    FeedbackSourceView,
    FeedbackView,
    PageView,
    SharedFeedbackCase,
)
from servicedesk_ai.services.conversations import ConversationService


class FeedbackService:
    """反馈不复制会话正文；共享撤销与处理竞争同一反馈行。"""

    def __init__(self, engine: AsyncEngine, conversations: ConversationService) -> None:
        """沿唯一会话授权与来源复核服务。"""
        self.engine, self.conversations = engine, conversations

    @staticmethod
    def require_knowledge_admin(actor: VerifiedIdentity) -> None:
        """管理动作要求当前身份明确具有知识管理员角色。"""
        if "KNOWLEDGE_ADMIN" not in actor.roles:
            raise BusinessError(CommonErrorCode.FORBIDDEN)

    async def list_shared(
        self, actor: VerifiedIdentity, page: int, size: int, status: str | None
    ) -> PageView[FeedbackQueueItem]:
        """分页列出已共享客户反馈；列表永不查询问题或回答正文。"""
        self.require_knowledge_admin(actor)
        if status not in {None, "PENDING", "RESOLVED", "DISMISSED"}:
            raise BusinessError(CommonErrorCode.INVALID_REQUEST)
        async with self.engine.connect() as db:
            params = {"limit": size, "offset": (page - 1) * size, "status": status}
            total = await queries.count_shared(db, params)
            rows = await queries.page_shared(db, params)
        return PageView(
            records=[self.queue_item(row) for row in rows],
            total=str(total),
            page_num=page,
            page_size=size,
        )

    @staticmethod
    def queue_item(row: RowMapping | Mapping[str, object]) -> FeedbackQueueItem:
        """在数据库投影边界校验字段，公开版本保留精度并恢复UTC时区。"""
        values = {name: row[name] for name in FeedbackQueueItem.model_fields}
        values["row_version"] = str(row["row_version"])
        result = FeedbackQueueItem.model_validate(values)
        return result.model_copy(update={"created_at": result.created_at.replace(tzinfo=UTC)})

    async def shared_case(self, actor: VerifiedIdentity, identifier: str) -> SharedFeedbackCase:
        """重新核验共享、会话及全部来源后，才返回本轮问题、回答与摘录。"""
        self.require_knowledge_admin(actor)
        async with self.engine.connect() as db:
            row = await queries.shared_case(db, {"id": identifier})
        if row is None:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        if row["generation_status"] != "SUCCEEDED" or row["answer_text"] is None:
            raise BusinessError(CommonErrorCode.CONFLICT)
        from servicedesk_ai.repositories.diagnosis import generation

        async with self.engine.connect() as db:
            run = await generation(db, row["generation_id"])
        sources = await self.conversations.evidence.verify(run)
        # 来源复核跨网络等待；交付正文前重新检查共享及会话删除，避免撤销期间泄漏。
        async with self.engine.connect() as db:
            current = await queries.shared_case(db, {"id": identifier})
        if current is None or current["generation_id"] != row["generation_id"]:
            raise BusinessError(CommonErrorCode.NOT_FOUND)
        row = current
        return SharedFeedbackCase(
            feedback=self.queue_item(row),
            comment=row["comment"],
            question=row["question_text"],
            answer=row["answer_text"],
            sources=[
                FeedbackSourceView(title=item.title, locator=item.locator, excerpt=item.excerpt)
                for item in sources
            ],
        )

    async def handle(
        self, actor: VerifiedIdentity, identifier: str, form: FeedbackHandle
    ) -> FeedbackQueueItem:
        """共享撤销、删除、重复处理及并发修改均按行锁和版本拒绝。"""
        self.require_knowledge_admin(actor)
        await self.shared_case(actor, identifier)
        async with self.engine.begin() as db:
            row = await queries.lock_feedback_case(db, {"id": identifier})
            if (
                row is None
                or not row["share_with_knowledge_admin"]
                or row["mode"] != "CUSTOMER_SELF_SERVICE"
                or row["deleted_at"] is not None
            ):
                raise BusinessError(CommonErrorCode.NOT_FOUND)
            if row["status"] != "PENDING" or row["row_version"] != int(form.row_version):
                raise BusinessError(CommonErrorCode.CONFLICT)
            status = "RESOLVED" if form.action == "RESOLVE" else "DISMISSED"
            await queries.handle_feedback(
                db,
                {
                    "status": status,
                    "actor": actor.actor.owner_id,
                    "note": form.handling_note.strip(),
                    "id": identifier,
                    "version": int(form.row_version),
                },
            )
            return self.queue_item(
                {**row, "status": status, "row_version": int(form.row_version) + 1}
            )

    async def put(self, actor: VerifiedIdentity, run_id: str, form: FeedbackInput) -> FeedbackView:
        """成功运行只保存一份本人评价；处理后不可PUT重置。"""
        run = await self.conversations.get_generation(actor, run_id)
        conversation = await self.conversations.authorized(actor, run.conversation_id)
        if run.status != "SUCCEEDED":
            raise BusinessError(CommonErrorCode.CONFLICT)
        async with self.engine.begin() as db:
            await queries.lock_generation(db, {"id": run_id})
            row = await queries.lock_generation_feedback(db, {"id": run_id})
            if row and row[1] in {"RESOLVED", "DISMISSED"}:
                raise BusinessError(CommonErrorCode.CONFLICT)
            status = (
                "NOT_REQUIRED"
                if form.rating == "HELPFUL"
                else "RECORDED"
                if conversation.mode == "TECHNICIAN_ASSIST"
                else "PENDING"
            )
            identifier = row[0] if row else new_id()
            if row:
                await queries.update_feedback(
                    db,
                    {
                        "id": identifier,
                        "rating": form.rating,
                        "category": form.issue_category,
                        "comment": form.comment,
                        "status": status,
                    },
                )
            else:
                await queries.insert_feedback(
                    db,
                    {
                        "id": identifier,
                        "run": run_id,
                        "actor": actor.actor.owner_id,
                        "rating": form.rating,
                        "category": form.issue_category,
                        "comment": form.comment,
                        "status": status,
                    },
                )
            return await self.view(db, identifier)

    async def share(
        self, actor: VerifiedIdentity, run_id: str, form: FeedbackShare
    ) -> FeedbackView:
        """仅客户自助可显式共享，当前来源无权时仍允许撤销已有共享。"""
        run = await self.conversations.get_generation(actor, run_id)
        conversation = await self.conversations.authorized(actor, run.conversation_id)
        if conversation.mode != "CUSTOMER_SELF_SERVICE" or (form.share and run.sources_hidden):
            raise BusinessError(CommonErrorCode.FORBIDDEN)
        async with self.engine.begin() as db:
            row = await queries.lock_feedback_sharing(db, {"id": run_id})
            if row is None:
                raise BusinessError(CommonErrorCode.NOT_FOUND)
            if form.share and (row[2] != "PENDING" or row[3] != "UNHELPFUL"):
                raise BusinessError(CommonErrorCode.CONFLICT)
            if row[1] != int(form.row_version):
                raise BusinessError(CommonErrorCode.CONFLICT)
            await queries.change_feedback_sharing(db, {"id": row[0], "share": form.share})
            return await self.view(db, row[0])

    async def view(self, db: AsyncConnection, identifier: str) -> FeedbackView:
        """只输出本人评价，不夹带私有会话、Prompt或知识正文。"""
        row = await queries.feedback_view(db, {"id": identifier})
        return FeedbackView(
            **{
                **row,
                "share_with_knowledge_admin": bool(row["share_with_knowledge_admin"]),
                "row_version": str(row["row_version"]),
            }
        )
