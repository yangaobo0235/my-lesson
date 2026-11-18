"""Redis原子日次数保护；跨库失败保守计数，不能把未知提交退还为免费重试。作者：yangaobo。"""

from datetime import datetime, timedelta, timezone
from inspect import isawaitable

from redis.asyncio import Redis
from redis.exceptions import RedisError

from servicedesk_ai.bootstrap.components import RedisSettings
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.errors.run import RunErrorCode

_RESERVE = """
local old=redis.call('HGET',KEYS[1],ARGV[1])
if old then
  if old==ARGV[2] or old==ARGV[4]..':'..ARGV[2] then return 1 else return -1 end
end
local used=0
for _, value in ipairs(redis.call('HVALS',KEYS[1])) do
  if not string.find(value, ':', 1, true) or string.sub(value,1,#ARGV[4]+1)==ARGV[4]..':' then
    used=used+1
  end
end
if used>=tonumber(ARGV[3]) then return 0 end
redis.call('HSET',KEYS[1],ARGV[1],ARGV[4]..':'..ARGV[2])
redis.call('EXPIRE',KEYS[1],172800)
return 1
"""


class RunQuota:
    """同日同键同义只预留一次；数据库未确认提交不主动退款。"""

    def __init__(self, redis: Redis, settings: RedisSettings) -> None:
        """连接归进程资源管理，键只含拥有者/日期/请求标识。"""
        self.redis, self.settings = redis, settings

    async def reserve(self, owner: str, key: str, digest: str, *, consult: bool = False) -> None:
        """独立计咨询与排障；旧版未分组预留保守占用两组，原键重放不再计数。

        不换日键或清计数，旧预留随原日期自然退出。Redis不可用拒绝新受理，
        已受理Run的查询和执行不依赖Redis。
        """
        day = datetime.now(timezone(timedelta(hours=8))).strftime("%Y%m%d")
        try:
            pending = self.redis.eval(
                _RESERVE,
                1,
                f"{self.settings.key_prefix}run-daily:{day}:{owner}",
                key,
                digest,
                self.settings.consult_daily_limit if consult else self.settings.daily_limit,
                "CONSULT" if consult else "DIAGNOSIS",
            )
            if not isawaitable(pending):
                raise TypeError("需要异步Redis连接")
            result = await pending
        except RedisError:
            raise BusinessError(CommonErrorCode.SERVICE_UNAVAILABLE) from None
        if result == -1:
            raise BusinessError(CommonErrorCode.CONFLICT)
        if result == 0:
            raise BusinessError(RunErrorCode.DAILY_RUN_LIMIT)
        if result != 1:
            raise BusinessError(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
