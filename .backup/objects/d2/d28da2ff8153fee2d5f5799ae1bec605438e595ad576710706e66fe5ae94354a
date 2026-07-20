"""
MessageRouter — 消息路由器
FastAPI Gateway 只负责收/发消息，不负责业务逻辑
所有消息路由到 MessageRouter，由它分发到各模块
"""
import json
import logging
from fastapi import WebSocket

from luo_king_server.core.protocol import envelope
from luo_king_server.server.device_manager import device_manager
from luo_king_server.server.session_manager import session_manager
from luo_king_server.server.connection_manager import connection_manager
from luo_king_server.server.battle_pipeline import pipeline

log = logging.getLogger("message_router")


class MessageRouter:
    """
    消息路由器
    Gateway 职责止于：收到消息 → 交给 Router → 拿到响应 → 发回
    Router 负责：类型判断 → 调用对应模块 → 返回响应
    """

    async def handle_heartbeat(self, ws: WebSocket, data: dict, seq: int) -> dict:
        """心跳/握手"""
        device_id = data.get("device_id", "unknown")
        battery = data.get("battery", 0)

        # 创建或获取 session
        sid = session_manager.get_session(device_id)
        if not sid:
            sid = session_manager.create(device_id)

        # 注册连接
        connection_manager.register(sid, ws)

        # 更新设备
        device_manager.touch(device_id, session_id=sid, battery=battery)

        log.info(f"[heartbeat] device={device_id} session={sid[:8]}")
        return envelope("heartbeat_ack", session_id=sid, seq=seq)

    async def handle_upload(self, ws: WebSocket, data: dict, seq: int) -> dict:
        """截图上传 → BattlePipeline"""
        device_id = data.get("device_id", "")
        sid = data.get("session_id", "")
        image = data.get("image", "")

        if not session_manager.validate(device_id, sid):
            return envelope("error", message="invalid session_id", seq=seq)

        device_manager.touch(device_id)
        info = device_manager.get(device_id)
        if info:
            info.total_uploads += 1

        action = await pipeline.process(device_id, sid, image)

        if info:
            info.total_actions += 1

        return action

    async def handle_action_result(self, ws: WebSocket, data: dict, seq: int) -> dict:
        """动作执行结果——只记录日志"""
        device_id = data.get("device_id", "")
        success = data.get("success", False)
        log.info(f"[action_result] device={device_id} success={success}")
        return {}  # 不需要回复

    async def route(self, ws: WebSocket, raw: str) -> dict:
        """路由入口——解析消息并分发"""
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            return envelope("error", message="invalid JSON")

        msg_type = data.get("type", "")
        seq = data.get("seq", 0)

        if msg_type == "heartbeat":
            return await self.handle_heartbeat(ws, data, seq)
        elif msg_type == "upload":
            return await self.handle_upload(ws, data, seq)
        elif msg_type == "action_result":
            return await self.handle_action_result(ws, data, seq)
        else:
            log.warning(f"[unknown] type={msg_type}")
            return envelope("error", message=f"unknown type: {msg_type}", seq=seq)


# 全局单例
router = MessageRouter()