"""
协议常量 — 唯一真理
所有消息使用统一信封格式：
{
    "version": 1,
    "trace_id": "5d7f...",
    "seq": 0,
    "ts": 1234567890.0,
    "type": "heartbeat",
    "device_id": "",
    "session_id": "",
    ...
}
"""
import time
import uuid

VERSION = 1

# ─── 消息类型（客户端 → 服务器） ───
HEARTBEAT = "heartbeat"
UPLOAD = "upload"
ACTION_RESULT = "action_result"

# ─── 消息类型（服务器 → 客户端） ───
HEARTBEAT_ACK = "heartbeat_ack"
ACTION = "action"
ERROR = "error"


def envelope(msg_type: str, **kwargs) -> dict:
    """生成统一消息信封（自动生成 trace_id）"""
    seq = kwargs.pop("seq", 0)
    return {
        "version": VERSION,
        "trace_id": uuid.uuid4().hex[:12],
        "seq": seq,
        "ts": time.time(),
        "type": msg_type,
        **kwargs
    }