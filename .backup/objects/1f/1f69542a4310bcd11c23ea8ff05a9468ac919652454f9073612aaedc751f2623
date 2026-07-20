"""
洛克王国 AI 服务器 — 主入口

FastAPI 只负责协议，不负责 AI。
所有 AI 逻辑在 BattlePipeline 中分层执行。

架构:
  FastAPI Gateway
  ├── HTTP: /health  /status  /metrics
  ├── WebSocket: /ws
  ├── DeviceManager (设备绑定/解绑)
  ├── SessionManager (会话生命周期)
  └── BattlePipeline (Vision → Engine → Strategy → Rule → Action)
"""
import json
import logging
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket, WebSocketDisconnect

from luo_king_server.server.device_manager import device_manager
from luo_king_server.server.session_manager import session_manager
from luo_king_server.server.battle_pipeline import pipeline

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s"
)
log = logging.getLogger("gateway")


# ─── 生命周期 ───

@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("🚀 Gateway 启动")
    yield
    log.info("🛑 Gateway 关闭")


app = FastAPI(
    title="洛克王国 AI Gateway",
    version="1.0.0",
    lifespan=lifespan
)


# ─── HTTP API（健康检查/状态监控） ───

@app.get("/health")
async def health():
    return {"status": "ok", "ts": time.time()}


@app.get("/status")
async def status():
    return {
        "devices": device_manager.count(),
        "sessions": session_manager.count(),
        "test_mode": pipeline.test_mode
    }


@app.get("/metrics")
async def metrics():
    devices = device_manager.all_devices()
    return {
        "devices": [
            {
                "device_id": d.device_id,
                "connected": d.connected,
                "session_id": d.session_id[:8] if d.session_id else "",
                "uploads": d.total_uploads,
                "actions": d.total_actions,
                "last_heartbeat": d.last_heartbeat,
                "battery": d.battery,
            }
            for d in devices
        ],
        "total_devices": len(devices),
        "total_sessions": session_manager.count(),
        "test_mode": pipeline.test_mode,
        "ts": time.time(),
    }


# ─── WebSocket ───

@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket):
    await ws.accept()
    log.info(f"[connect] {ws.client}")

    current_device_id: str = ""

    try:
        while True:
            raw = await ws.receive_text()

            # ── 解析 ──
            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                await ws.send_text(json.dumps({"type": "error", "message": "invalid JSON"}))
                continue

            msg_type = data.get("type", "")
            device_id = data.get("device_id", "")

            if not device_id:
                await ws.send_text(json.dumps({"type": "error", "message": "missing device_id"}))
                continue

            # 首次消息：绑定设备
            if not current_device_id:
                current_device_id = device_id
                device_manager.bind(device_id, ws)

            # ── heartbeat：握手 + 会话管理 ──
            if msg_type == "heartbeat":
                # 创建或获取 session
                sid = session_manager.get_session(device_id)
                if not sid:
                    sid = session_manager.create(device_id)

                # 更新设备状态
                info = device_manager.get(device_id)
                if info:
                    info.last_heartbeat = time.time()
                    info.session_id = sid
                    info.battery = data.get("battery", info.battery)

                await ws.send_text(json.dumps({
                    "type": "heartbeat_ack",
                    "session_id": sid
                }))
                log.info(f"[heartbeat] device={device_id} session={sid[:8]}")

            # ── upload：截图 → BattlePipeline → action ──
            elif msg_type == "upload":
                sid = data.get("session_id", "")
                image = data.get("image", "")

                # 验证 session
                if not session_manager.validate(device_id, sid):
                    await ws.send_text(json.dumps({
                        "type": "error",
                        "message": "invalid session_id"
                    }))
                    continue

                # 更新设备统计
                info = device_manager.get(device_id)
                if info:
                    info.total_uploads += 1

                # 进入战斗流水线
                action = await pipeline.process(device_id, sid, image)
                await ws.send_text(json.dumps(action))

                if info:
                    info.total_actions += 1

            # ── action_result ──
            elif msg_type == "action_result":
                success = data.get("success", False)
                log.info(f"[action_result] device={device_id} success={success}")

            else:
                log.warning(f"[unknown] device={device_id} type={msg_type}")

    except WebSocketDisconnect:
        log.info(f"[disconnect] device={current_device_id} ({ws.client})")
    except Exception as e:
        log.error(f"[error] device={current_device_id}: {e}")
    finally:
        if current_device_id:
            device_manager.unbind(current_device_id)
        log.info(f"[cleanup] device={current_device_id}")


# ─── 启动入口 ───

if __name__ == "__main__":
    import uvicorn
    log.info("=" * 50)
    log.info("  洛克王国 AI Gateway")
    log.info(f"  WebSocket: ws://0.0.0.0:8765/ws")
    log.info(f"  HTTP:      http://0.0.0.0:8765/health")
    log.info(f"  Test mode: {pipeline.test_mode}")
    log.info("=" * 50)
    uvicorn.run(app, host="0.0.0.0", port=8765, log_level="info")