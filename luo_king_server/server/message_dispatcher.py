"""
MessageDispatcher — 消息分发器
注册化 handler，无 if/else
每个消息类型对应一个独立 handler
"""
import logging
from typing import Callable, Awaitable
from fastapi import WebSocket

log = logging.getLogger("message_dispatcher")


class MessageDispatcher:
    """
    消息分发器
    通过 register() 注册 handler，取代 if/elif 链
    handler 签名: async def handler(ws, data, seq) -> dict
    """
    def __init__(self):
        self._handlers: dict[str, Callable] = {}

    def register(self, msg_type: str, handler: Callable[..., Awaitable[dict]]):
        """注册消息类型处理器"""
        self._handlers[msg_type] = handler
        log.info(f"[register] msg_type={msg_type}")

    async def dispatch(self, ws: WebSocket, data: dict) -> dict:
        """分发消息到对应 handler"""
        msg_type = data.get("type", "")
        seq = data.get("seq", 0)
        handler = self._handlers.get(msg_type)
        if not handler:
            log.warning(f"[unknown] type={msg_type}")
            from luo_king_server.core.protocol import envelope
            return envelope("error", message=f"unknown type: {msg_type}", seq=seq)
        return await handler(ws, data, seq)


# 全局单例
dispatcher = MessageDispatcher()