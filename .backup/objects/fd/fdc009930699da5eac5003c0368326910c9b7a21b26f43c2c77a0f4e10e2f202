"""
DeviceManager — 设备管理
device_id → { websocket, session, heartbeat, battery, current_state }
WebSocket 断开后重新绑定，session 不丢失
"""
import time
import logging
from typing import Optional
from fastapi import WebSocket

log = logging.getLogger("device_manager")


class DeviceInfo:
    """单个设备的状态"""
    def __init__(self, device_id: str):
        self.device_id: str = device_id
        self.ws: Optional[WebSocket] = None
        self.session_id: str = ""
        self.last_heartbeat: float = 0.0
        self.battery: int = 0
        self.connected: bool = False
        self.total_uploads: int = 0
        self.total_actions: int = 0

    def bind(self, ws: WebSocket):
        """绑定新的 WebSocket 连接（断线重连后重新绑定）"""
        self.ws = ws
        self.connected = True

    def unbind(self):
        """解绑 WebSocket（连接断开后，保留 session 和其他状态）"""
        self.ws = None
        self.connected = False

    @property
    def is_alive(self) -> bool:
        return self.connected and (time.time() - self.last_heartbeat) < 30


class DeviceManager:
    """
    设备管理器 — 全局唯一
    不绑定 WebSocket 生命周期，设备断线后 session 保留
    """
    def __init__(self):
        self._devices: dict[str, DeviceInfo] = {}

    def get_or_create(self, device_id: str) -> DeviceInfo:
        if device_id not in self._devices:
            self._devices[device_id] = DeviceInfo(device_id)
        return self._devices[device_id]

    def get(self, device_id: str) -> Optional[DeviceInfo]:
        return self._devices.get(device_id)

    def bind(self, device_id: str, ws: WebSocket):
        info = self.get_or_create(device_id)
        info.bind(ws)
        log.info(f"[bind] device={device_id} session={info.session_id}")

    def unbind(self, device_id: str):
        info = self._devices.get(device_id)
        if info:
            info.unbind()
            log.info(f"[unbind] device={device_id}")

    def remove(self, device_id: str):
        self._devices.pop(device_id, None)
        log.info(f"[remove] device={device_id}")

    def all_devices(self) -> list[DeviceInfo]:
        return list(self._devices.values())

    def count(self) -> int:
        return len(self._devices)


# 全局单例
device_manager = DeviceManager()