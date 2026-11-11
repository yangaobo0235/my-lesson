"""用与Java相同的固定线向量核对生成协议与optional语义。"""

from desk_contracts.common.v1.common_pb2 import ActorRef, RpcErrorDetail


def test_actor_wire_contract_preserves_large_version() -> None:
    """认证版本超过JavaScript安全范围时仍保留准确int64。"""
    session = "12345678-1234-1234-1234-123456789012"
    wire = "0a20" + "61" * 32 + "1224" + session.encode().hex() + "188180808080808010"
    actor = ActorRef(owner_id="a" * 32, session_ref=session, auth_version=9007199254740993)
    assert actor.SerializeToString().hex() == wire
    assert ActorRef.FromString(bytes.fromhex(wire)) == actor


def test_optional_retry_distinguishes_absence_and_zero() -> None:
    """不存在的退避时间与显式零值不能在生成或打包时混淆。"""
    absent = RpcErrorDetail(code=1004)
    zero = RpcErrorDetail(code=1004, retry_after_ms=0)
    assert not absent.HasField("retry_after_ms")
    assert RpcErrorDetail.FromString(zero.SerializeToString()).HasField("retry_after_ms")
    assert zero.SerializeToString().hex() == "08ec072000"
