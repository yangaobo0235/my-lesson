"""配额依赖失败、原键冲突和咨询资源组边界回归。作者：yangaobo。"""

from unittest.mock import AsyncMock

import pytest
from redis.exceptions import ConnectionError

from servicedesk_ai.bootstrap.components import RedisSettings
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.run import RunErrorCode
from servicedesk_ai.infrastructure.run_quota import RunQuota


@pytest.mark.asyncio
@pytest.mark.parametrize("consult,limit,group", [(True, 5, "CONSULT"), (False, 20, "DIAGNOSIS")])
async def test_modes_use_original_key_with_separate_limits(consult, limit, group):
    """同一原键空间保存模式，避免迁移重置额度或跨模式绕过请求幂等。"""
    redis = AsyncMock()
    redis.eval.return_value = 1
    await RunQuota(redis, RedisSettings()).reserve("owner", "request", "digest", consult=consult)
    args = redis.eval.call_args.args
    assert args[1] == 1 and args[2].startswith("desk:test:ai:run-daily:")
    assert args[2].endswith(":owner")
    assert args[3:] == ("request", "digest", limit, group)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "result,error",
    [
        (0, RunErrorCode.DAILY_RUN_LIMIT),
        (-1, CommonErrorCode.CONFLICT),
        (8, CommonErrorCode.UPSTREAM_PROTOCOL_ERROR),
    ],
)
async def test_rejected_or_unknown_reservation_never_admits(result, error):
    """额度不足、同键异义和未知Redis协议结果各自明确失败。"""
    redis = AsyncMock()
    redis.eval.return_value = result
    with pytest.raises(BusinessError) as failure:
        await RunQuota(redis, RedisSettings()).reserve("owner", "request", "digest")
    assert failure.value.error == error


@pytest.mark.asyncio
async def test_redis_disconnect_does_not_retry_unknown_reservation():
    """连接中断不证明Lua未提交，不自动重试或退还。"""
    redis = AsyncMock()
    redis.eval.side_effect = ConnectionError("private dependency detail")
    with pytest.raises(BusinessError) as failure:
        await RunQuota(redis, RedisSettings()).reserve("owner", "request", "digest")
    assert failure.value.error == CommonErrorCode.SERVICE_UNAVAILABLE
    assert redis.eval.await_count == 1
