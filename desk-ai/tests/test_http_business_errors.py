"""公开HTTP异常合同按登记编号及语义返回，不能遗漏已注册的业务异常类型。作者：yangaobo。"""

import httpx
import pytest

from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.errors.common import BusinessError
from servicedesk_ai.errors.draft import DraftErrorCode
from servicedesk_ai.errors.evidence import EvidenceErrorCode
from servicedesk_ai.errors.retrieval import RetrievalErrorCode
from servicedesk_ai.errors.run import RunErrorCode
from servicedesk_ai.main import create_app


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("error", "http_status", "code"),
    [
        (EvidenceErrorCode.SOURCE_WITHDRAWN, 409, 5103),
        (RetrievalErrorCode.RETRIEVAL_UNAVAILABLE, 503, 5101),
        (RunErrorCode.RUN_QUEUE_FULL, 503, 5001),
        (DraftErrorCode.DRAFT_EXPIRED, 409, 5201),
    ],
)
async def test_registered_business_error_reaches_http_contract(error, http_status, code):
    """走真实异常处理链，验证错误类型不会被错误降级成通用500。"""
    app = create_app(Settings(infrastructure_enabled=False))

    @app.get("/failure")
    async def failure():
        raise BusinessError(error)

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get("/failure")
    assert response.status_code == http_status
    body = response.json()
    assert body["code"] == code and body["data"] is None
    assert body["message"] == error.message and body["traceId"]
