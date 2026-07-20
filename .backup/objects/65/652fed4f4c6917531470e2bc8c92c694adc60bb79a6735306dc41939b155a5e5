"""
ConnectionManager — 连接管理
session_id → WebSocket
不与 DeviceManager 耦合，支持断线重连后重新绑定
"""
import logging
from typing import Optional
from fastapi import WebSocket

log = logging.getLogger("connection_manager")


class ConnectionManager:
    """
    连接管理器
    只负责 session_id → WebSocket 的映射
    DeviceManager 不保存 WebSocket，ConnectionManager 不保存 device_id
    """
    def __init__(self):
        self._connections: dict[str, WebSocket] = {}  # session_id → WebSocket

    def register(self, session_id: str, ws: WebSocket):
        old = self._connections.get(session_id)
        if old:
            log.info(f"[register] replacing old connection for session={session_id[:8]}")
        self._connections[session_id] = ws

    def unregister(self, session_id: str):
        self._connections.pop(session_id, None)

    def get(self, session_id: str) -> Optional[WebSocket]:
        return self._connections.get(session_id)

    def send(self, session_id: str, data: dict) -> bool:
        """发送消息，返回是否成功"""
        ws = self._connections.get(session_id)
        if not ws:
            return False
        try:
            import json
            import asyncio
            asyncio.ensure_future(ws.send_text(json.dumps(data)))
            return True
        except Exception as e:
            log.error(f"[send] session={session_id[:8]}: {e}")
            return False

    def count(self) -> int:
        return len(self._connections)

    def all_sessions(self) -> list[str]:
        return list(self._connections.keys())


# 全局单例
connection_manager = ConnectionManager()