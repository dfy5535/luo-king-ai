"""
MessageBus — 消息总线
Gateway → MessageBus → Pipeline + Services
所有消费者（Pipeline、Replay、Statistics、Learning、Remote Control）都通过 MessageBus 订阅
"""
import logging
import time
from typing import Callable, Awaitable

log = logging.getLogger("message_bus")


class MessageBus:
    """
    消息总线
    任何模块都可以订阅消息类型：
    - bus.subscribe("upload", pipeline_handler)
    - bus.subscribe("upload", replay_recorder)
    - bus.subscribe("action_result", statistics_collector)
    """
    def __init__(self):
        self._subscribers: dict[str, list[Callable]] = {}

    def subscribe(self, msg_type: str, handler: Callable[..., Awaitable[dict]]):
        """订阅消息类型"""
        if msg_type not in self._subscribers:
            self._subscribers[msg_type] = []
        self._subscribers[msg_type].append(handler)
        log.info(f"[subscribe] type={msg_type} handler={handler.__name__}")

    async def publish(self, msg_type: str, **kwargs) -> list[dict]:
        """发布消息到所有订阅者"""
        results = []
        handlers = self._subscribers.get(msg_type, [])
        if not handlers:
            log.debug(f"[publish] type={msg_type} no subscribers")
            return results

        for handler in handlers:
            try:
                result = await handler(**kwargs)
                if result:
                    results.append(result)
            except Exception as e:
                log.error(f"[publish] type={msg_type} handler={handler.__name__}: {e}")
        return results

    def count_subscribers(self, msg_type: str = "") -> int:
        if msg_type:
            return len(self._subscribers.get(msg_type, []))
        return sum(len(v) for v in self._subscribers.values())


# 全局单例
bus = MessageBus()