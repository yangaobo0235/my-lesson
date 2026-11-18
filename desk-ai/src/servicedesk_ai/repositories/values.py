"""AI持久化的业务标识与紧凑JSON编码，不携带数据库连接或业务状态。作者：yangaobo。"""

import json
from uuid import uuid4


def new_id() -> str:
    """所属服务生成32位业务标识，与执行租约令牌分开。"""
    return uuid4().hex


def json_value(value: object) -> str:
    """以UTF-8兼容的紧凑JSON编码已校验持久值，不保存资源或认证对象。"""
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
