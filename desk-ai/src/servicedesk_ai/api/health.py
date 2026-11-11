"""提供不调用付费模型或外部存储的进程健康探针。"""

from fastapi import APIRouter

router = APIRouter()


@router.get("/health", include_in_schema=False)
async def health() -> dict[str, str]:
    """报告当前HTTP进程存活，不证明业务依赖或双监听已经就绪。"""
    return {"status": "UP"}
