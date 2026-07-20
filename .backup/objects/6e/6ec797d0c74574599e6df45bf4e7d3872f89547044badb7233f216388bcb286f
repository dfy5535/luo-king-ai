"""
SessionManager — 会话生命周期管理
session_id 生成、验证、过期清理
"""
import uuid
import time
import logging
from typing import Optional

log = logging.getLogger("session_manager")


class SessionInfo:
    def __init__(self, device_id: str):
        self.session_id: str = uuid.uuid4().hex[:12]
        self.device_id: str = device_id
        self.created_at: float = time.time()
        self.last_active: float = time.time()
        self.battle_id: str = ""

    def touch(self):
        self.last_active = time.time()

    @property
    def age(self) -> float:
        return time.time() - self.created_at

    @property
    def idle(self) -> float:
        return time.time() - self.last_active


class SessionManager:
    """
    会话管理器
    session_id 与 WebSocket 解耦，只与 device_id 绑定
    """
    def __init__(self, session_timeout: float = 300.0):
        self._sessions: dict[str, SessionInfo] = {}  # session_id -> SessionInfo
        self._device_sessions: dict[str, str] = {}    # device_id -> session_id
        self._timeout = session_timeout

    def create(self, device_id: str) -> str:
        """为设备创建新 session，替换旧 session"""
        old_sid = self._device_sessions.get(device_id)
        if old_sid:
            self._sessions.pop(old_sid, None)

        info = SessionInfo(device_id)
        self._sessions[info.session_id] = info
        self._device_sessions[device_id] = info.session_id
        log.info(f"[create] device={device_id} session={info.session_id}")
        return info.session_id

    def validate(self, device_id: str, session_id: str) -> bool:
        """验证 session_id 是否匹配 device_id"""
        info = self._sessions.get(session_id)
        if not info:
            return False
        if info.device_id != device_id:
            return False
        info.touch()
        return True

    def get_device_id(self, session_id: str) -> Optional[str]:
        info = self._sessions.get(session_id)
        return info.device_id if info else None

    def get_session(self, device_id: str) -> Optional[str]:
        return self._device_sessions.get(device_id)

    def remove(self, session_id: str):
        info = self._sessions.pop(session_id, None)
        if info:
            self._device_sessions.pop(info.device_id, None)
            log.info(f"[remove] session={session_id} device={info.device_id}")

    def cleanup_stale(self):
        """清理超时会话"""
        now = time.time()
        stale = [sid for sid, info in self._sessions.items()
                 if now - info.last_active > self._timeout]
        for sid in stale:
            self.remove(sid)
        if stale:
            log.info(f"[cleanup] removed {len(stale)} stale sessions")

    def count(self) -> int:
        return len(self._sessions)


# 全局单例
session_manager = SessionManager()