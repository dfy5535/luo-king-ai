"""
luo_king_server/server/test_gateway.py
========================================
测试模式网关——不依赖任何 AI 模块。

用于验证 APK 最小闭环：
  截图 → upload → 服务器返回固定动作 → 客户端执行 → action_result

收到任何 upload 都返回固定 tap 动作，不调用视觉/引擎/策略/规则。
"""

from __future__ import annotations
import asyncio
import json
import logging
import time
import uuid
from typing import Dict, Optional

logger = logging.getLogger(__name__)

try:
    import websockets
except ImportError:
    websockets = None


class TestGateway:
    """
    测试网关——只做三件事：
    1. 响应 heartbeat，分配 session_id
    2. 收到 upload 后返回固定 tap 动作
    3. 记录 action_result
    """

    def __init__(self):
        self.sessions: Dict[str, dict] = {}
        self.websocket_map: Dict[str, object] = {}
        self.total_uploads = 0
        self.total_actions_sent = 0

    async def handle_message(self, websocket, message: str):
        try:
            data = json.loads(message)
            msg_type = data.get("type", "")
            if msg_type == "heartbeat":
                await self._handle_heartbeat(websocket, data)
            elif msg_type == "upload":
                await self._handle_upload(websocket, data)
            elif msg_type == "action_result":
                await self._handle_action_result(data)
            else:
                await self._send(websocket, {
                    "type": "error",
                    "message": f"未知消息类型: {msg_type}",
                    "code": "UNKNOWN_TYPE"
                })
        except json.JSONDecodeError:
            await self._send(websocket, {
                "type": "error", "message": "JSON格式错误", "code": "INVALID_JSON"
            })
        except Exception as e:
            logger.error(f"处理失败: {e}", exc_info=True)
            await self._send(websocket, {
                "type": "error", "message": str(e), "code": "INTERNAL_ERROR"
            })

    async def _handle_heartbeat(self, websocket, data: dict):
        device_id = data.get("device_id", "")
        if device_id not in self.sessions:
            self.sessions[device_id] = {
                "session_id": uuid.uuid4().hex[:12],
                "device_id": device_id,
                "last_heartbeat": time.time(),
                "uploads": 0,
            }
            self.websocket_map[device_id] = websocket
            logger.info(f"[TEST] 新设备连接: {device_id}, session_id={self.sessions[device_id]['session_id']}")
        else:
            existing = self.sessions[device_id]
            old_ws = self.websocket_map.get(device_id)
            if old_ws != websocket:
                logger.info(f"[TEST] 设备重连: {device_id}, 生成新session_id")
                existing["session_id"] = uuid.uuid4().hex[:12]
                self.websocket_map[device_id] = websocket
            existing["last_heartbeat"] = time.time()

        session = self.sessions[device_id]
        await self._send(websocket, {
            "type": "heartbeat_ack",
            "server_time": int(time.time()),
            "session_id": session["session_id"],
        })

    async def _handle_upload(self, websocket, data: dict):
        device_id = data.get("device_id", "")
        session_id = data.get("session_id", "")
        image_len = len(data.get("image", ""))

        # session_id 校验
        if not session_id or device_id not in self.sessions:
            await self._send(websocket, {
                "type": "action", "action_type": "wait",
                "delay_ms": 1000, "reasoning": "未授权，请先完成心跳握手",
                "session_id": session_id,
            })
            return

        session = self.sessions[device_id]
        if session["session_id"] != session_id:
            await self._send(websocket, {
                "type": "action", "action_type": "wait",
                "delay_ms": 1000, "reasoning": "session_id 不匹配，请重新连接",
                "session_id": session_id,
            })
            return

        self.total_uploads += 1
        session["uploads"] += 1
        logger.info(f"[TEST] 收到截图: device={device_id}, size={image_len}B, total={self.total_uploads}")

        # ── 返回固定动作：点击屏幕中央 ──
        # 模拟服务器决策，不依赖任何 AI
        await self._send(websocket, {
            "type": "action",
            "action_type": "tap",
            "coordinate": [540, 960],  # 屏幕中央
            "delay_ms": 500,
            "reasoning": f"[测试模式] 固定动作 #{self.total_uploads}",
            "session_id": session_id,
            "skill_index": 0,
        })
        self.total_actions_sent += 1

    async def _handle_action_result(self, data: dict):
        device_id = data.get("device_id", "")
        success = data.get("success", False)
        logger.info(f"[TEST] 执行结果: device={device_id}, success={success}")

    async def _send(self, websocket, data: dict):
        try:
            await websocket.send(json.dumps(data, ensure_ascii=False))
        except Exception as e:
            logger.error(f"[TEST] 发送失败: {e}")

    async def on_connect(self, websocket):
        logger.info(f"[TEST] 新WebSocket连接")

    async def on_disconnect(self, websocket):
        for device_id, ws in list(self.websocket_map.items()):
            if ws == websocket:
                logger.info(f"[TEST] 设备断开: {device_id}")
                self.sessions.pop(device_id, None)
                self.websocket_map.pop(device_id, None)
                break


async def run_test_server(host: str = "0.0.0.0", port: int = 8765):
    """启动测试模式服务器"""
    if websockets is None:
        logger.error("websockets 未安装: pip install websockets")
        return

    gateway = TestGateway()

    async def handler(websocket):
        await gateway.on_connect(websocket)
        try:
            async for message in websocket:
                await gateway.handle_message(websocket, message)
        finally:
            await gateway.on_disconnect(websocket)

    logger.info(f"🧪 测试模式服务器启动: ws://{host}:{port}")
    logger.info(f"    收到 upload 后返回固定 tap 动作，不依赖任何 AI")
    logger.info(f"    按 Ctrl+C 停止")

    async def start():
        ws_server = await websockets.serve(handler, host, port)
        await asyncio.Future()

    await start()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(levelname)s: %(message)s")
    asyncio.run(run_test_server())