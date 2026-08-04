from typing import Annotated, Any
from uuid import UUID, uuid4

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from pydantic import Field

from mylesson_agent.api.dependencies import container
from mylesson_agent.container import Container
from mylesson_agent.domain.api_models import ApiModel, AuthenticatedUser
from mylesson_agent.security.delegation import require_user

router = APIRouter(prefix="/api/v1/ai")


class RecommendationRequest(ApiModel):
    goal: str = Field(min_length=1, max_length=200)
    limit: int = Field(default=5, ge=1, le=8)


class AdjustmentRequest(ApiModel):
    adjustment: str = Field(min_length=1, max_length=1000)
    request_id: UUID


class ProgressRequest(ApiModel):
    progress_percent: int = Field(ge=0, le=100)
    note: str | None = Field(default=None, max_length=500)


class CreateLearningPlanDraftRequest(ApiModel):
    request_id: UUID
    goal: str = Field(min_length=1, max_length=500)
    minutes_per_day: int = Field(default=30, ge=10, le=480)
    courses: list[dict[str, Any]] = Field(min_length=1, max_length=20)
    daily_routine: list[dict[str, Any]] = Field(min_length=1, max_length=20)


def _headers(app: Container, user: AuthenticatedUser) -> dict[str, str]:
    headers = {"X-ML-Delegation": user.delegation_token, "X-Request-Id": str(uuid4())}
    if app.settings.service_token:
        headers["X-Internal-Token"] = app.settings.service_token.get_secret_value()
    return headers


async def _proxy(
    method: str,
    url: str,
    app: Container,
    user: AuthenticatedUser,
    *,
    json: dict[str, Any] | None = None,
    params: dict[str, Any] | None = None,
) -> Any:
    async with httpx.AsyncClient(timeout=20.0) as client:
        response = await client.request(
            method, url, headers=_headers(app, user), json=json, params=params
        )
    if response.status_code == 204:
        return None
    if response.status_code >= 400:
        raise HTTPException(response.status_code, detail="Java business operation failed")
    payload = response.json()
    if isinstance(payload, dict) and "code" in payload:
        if payload.get("code") != 1000:
            raise HTTPException(status_code=502, detail="Java business operation failed")
        return payload.get("data")
    return payload


@router.post("/course-recommendations")
async def recommend_courses(
    request: RecommendationRequest,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    app: Annotated[Container, Depends(container)],
) -> dict[str, Any]:
    result = await app.tool_client.execute(
        "search_courses",
        {"keyword": request.goal, "limit": request.limit},
        user,
        str(uuid4()),
    )
    if not result.success:
        raise HTTPException(status_code=502, detail="Course service unavailable")
    courses = result.data if isinstance(result.data, list) else []
    recommended = []
    for priority, course in enumerate(courses[: request.limit], start=1):
        recommended.append(
            {
                "courseId": course.get("id"),
                "title": course.get("title"),
                "author": course.get("author"),
                "category": course.get("category"),
                "price": course.get("price"),
                "cover": course.get("cover"),
                "reason": f"与目标“{request.goal}”相关",
                "priority": priority,
                "estimatedHours": 0,
                "owned": False,
                "inCart": False,
                "citations": [],
            }
        )
    return {
        "goal": request.goal,
        "summary": f"找到{len(recommended)}门相关课程",
        "recommendedCourses": recommended,
        "nextAction": "可以选择课程后让AI生成学习计划候选。",
    }


@router.get("/learning-plan-drafts")
async def list_drafts(
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    app: Annotated[Container, Depends(container)],
) -> Any:
    return await _proxy(
        "GET",
        f"{app.settings.course_service_url}/internal/v1/agent/me/learning-plan-drafts",
        app,
        user,
    )


@router.post("/learning-plan-drafts")
async def create_draft(
    request: CreateLearningPlanDraftRequest,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    app: Annotated[Container, Depends(container)],
) -> Any:
    return await _proxy(
        "POST",
        f"{app.settings.course_service_url}/internal/v1/agent/me/learning-plan-drafts",
        app,
        user,
        json=request.model_dump(by_alias=True, mode="json"),
    )


@router.get("/learning-plan-drafts/{draft_id}/versions")
async def draft_versions(
    draft_id: UUID,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    app: Annotated[Container, Depends(container)],
) -> Any:
    return await _proxy(
        "GET",
        f"{app.settings.course_service_url}/internal/v1/agent/me/learning-plan-drafts/{draft_id}/versions",
        app,
        user,
    )


@router.post("/learning-plan-drafts/{draft_id}/adjustments")
async def adjust_draft(
    draft_id: UUID,
    request: AdjustmentRequest,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    app: Annotated[Container, Depends(container)],
) -> Any:
    return await _proxy(
        "POST",
        f"{app.settings.course_service_url}/internal/v1/agent/me/learning-plan-drafts/{draft_id}/adjustments",
        app,
        user,
        json=request.model_dump(by_alias=True, mode="json"),
    )


@router.post("/learning-plan-drafts/{draft_id}/confirm")
async def confirm_draft(
    draft_id: UUID,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    app: Annotated[Container, Depends(container)],
) -> Any:
    return await _proxy(
        "POST",
        f"{app.settings.course_service_url}/internal/v1/agent/me/learning-plan-drafts/{draft_id}/confirm",
        app,
        user,
    )


@router.post("/learning-plan-drafts/{draft_id}/cancel", status_code=status.HTTP_204_NO_CONTENT)
async def cancel_draft(
    draft_id: UUID,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    app: Annotated[Container, Depends(container)],
) -> Response:
    await _proxy(
        "POST",
        f"{app.settings.course_service_url}/internal/v1/agent/me/learning-plan-drafts/{draft_id}/cancel",
        app,
        user,
    )
    return Response(status_code=204)


@router.get("/plans")
async def plans(
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    app: Annotated[Container, Depends(container)],
    limit: int = Query(default=50, ge=1, le=100),
) -> Any:
    return await _proxy(
        "GET",
        f"{app.settings.course_service_url}/internal/v1/agent/me/learning-plans",
        app,
        user,
        params={"limit": limit},
    )


@router.patch("/plans/{plan_id}/progress")
async def update_progress(
    plan_id: UUID,
    request: ProgressRequest,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
    app: Annotated[Container, Depends(container)],
) -> Any:
    return await _proxy(
        "PATCH",
        f"{app.settings.course_service_url}/internal/v1/agent/me/learning-plans/{plan_id}/progress",
        app,
        user,
        json=request.model_dump(by_alias=True),
    )
