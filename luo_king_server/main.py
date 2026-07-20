"""
洛克王国 AI 服务器 — 主入口

最终架构（冻结版，半年内不改）:

Android APK
├── CaptureManager
├── InputManager
├── ConnectionManager
└── ConfigManager
        │
        ▼
WebSocket (/ws)
        │
        ▼
FastAPI Gateway
    ├── ConnectionManager    ← session_id → WebSocket
    ├── MessageDispatcher    ← 注册化 handler，无 if/else
    ├── SessionManager       ← session_id → device_id
    ├── DeviceManager        ← device_id → stats
    ├── MessageBus           ← 消息总线（Pipeline/Replay/Statistics 等订阅）
    └── BattlePipeline       ← 可插拔流水线
           ├── VisionPlugin
           ├── MemoryPlugin
           ├── EnginePlugin
           ├── StrategyPlugin
           ├── RulePlugin
           ├── ReplayPlugin
           └── LearningPlugin
        │
        ▼
HTTP: /health /status /metrics
"""
import json
import logging
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket, WebSocketDisconnect

from luo_king_server.core.protocol import envelope, decision_envelope
from luo_king_server.core.models import BattleContext
from luo_king_server.server.connection_manager import connection_manager
from luo_king_server.server.device_manager import device_manager
from luo_king_server.server.session_manager import session_manager
from luo_king_server.server.message_dispatcher import dispatcher
from luo_king_server.server.message_bus import bus
from luo_king_server.server.battle_pipeline import pipeline

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s"
)
log = logging.getLogger("gateway")


# ─── 生命周期 ───

@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("=" * 50)
    log.info("  🚀 洛克王国 AI Gateway 启动")
    log.info(f"  Pipeline: {pipeline.plugin_names() or '[测试模式]'}")
    log.info("=" * 50)
    yield
    log.info("🛑 Gateway 关闭")


app = FastAPI(
    title="洛克王国 AI Gateway",
    version="2.0.0",
    lifespan=lifespan
)


# ─── Handlers（注册到 MessageDispatcher） ───

async def handle_heartbeat(ws: WebSocket, data: dict, seq: int) -> dict:
    device_id = data.get("device_id", "unknown")
    battery = data.get("battery", 0)

    sid = session_manager.get_session(device_id)
    if not sid:
        sid = session_manager.create(device_id)

    connection_manager.register(sid, ws)
    device_manager.touch(device_id, session_id=sid, battery=battery)

    log.info(f"[heartbeat] device={device_id} session={sid[:8]}")
    return envelope("heartbeat_ack", session_id=sid, seq=seq)


async def handle_upload(ws: WebSocket, data: dict, seq: int) -> dict:
    device_id = data.get("device_id", "")
    sid = data.get("session_id", "")
    image = data.get("image", "")

    if not session_manager.validate(device_id, sid):
        return envelope("error", message="invalid session_id", seq=seq)

    device_manager.touch(device_id)
    info = device_manager.get(device_id)
    if info:
        info.total_uploads += 1

    # 发送 thought 1: 收到截图
    await ws.send_text(json.dumps(envelope("thought", text="📸 收到截图，正在分析战场...", seq=seq)))

    # 构建 BattleContext
    ctx = BattleContext(
        device_id=device_id,
        session_id=sid,
        trace_id=data.get("trace_id", uuid.uuid4().hex[:12]),
        screenshot=image
    )

    # 通过 MessageBus 发布
    await bus.publish("upload", ctx=ctx, data=data)

    # 发送 thought 2: 调用 Pipeline
    await ws.send_text(json.dumps(envelope("thought", text="🧠 分析敌方配置...", seq=seq)))

    # 执行 Pipeline
    ctx = await pipeline.execute(ctx)

    # 从 context 中提取决策
    action = ctx.final_action if ctx.final_action else {
        "type": "decision",
        "think": {"stage": "分析", "reason": "默认决策", "confidence": 0.5},
        "decision": {"action": "wait", "target": "", "reason": "无数据"},
        "execute": {"type": "tap", "coordinate": [540, 960], "delay_ms": 500, "session_id": sid}
    }

    # 发送 thought 3: 决策完成
    decision_action = action.get("decision", {}).get("action", "?")
    think_reason = action.get("think", {}).get("reason", "?")
    await ws.send_text(json.dumps(envelope("thought",
        text=f"✅ 决策: {decision_action} — {think_reason}",
        seq=seq)))

    if info:
        info.total_actions += 1

    return action


async def handle_action_result(ws: WebSocket, data: dict, seq: int) -> dict:
    device_id = data.get("device_id", "")
    success = data.get("success", False)
    log.info(f"[action_result] device={device_id} success={success}")

    # 通过 MessageBus 发布（Statistics/Replay 等订阅）
    await bus.publish("action_result", device_id=device_id, success=success, data=data)
    return {}


# 注册 handler
dispatcher.register("heartbeat", handle_heartbeat)
dispatcher.register("upload", handle_upload)
dispatcher.register("action_result", handle_action_result)


# ─── HTTP API ───

@app.get("/health")
async def health():
    return {"status": "ok", "ts": time.time()}


@app.get("/status")
async def status():
    return {
        "devices": device_manager.count(),
        "sessions": session_manager.count(),
        "connections": connection_manager.count(),
        "test_mode": pipeline.test_mode,
        "pipeline_plugins": pipeline.plugin_names(),
        "bus_subscribers": {
            "upload": bus.count_subscribers("upload"),
            "action_result": bus.count_subscribers("action_result"),
        }
    }


@app.get("/metrics")
async def metrics():
    devices = device_manager.all_devices()
    return {
        "devices": [
            {
                "device_id": d.device_id,
                "session_id": d.session_id[:8] if d.session_id else "",
                "uploads": d.total_uploads,
                "actions": d.total_actions,
                "last_heartbeat": d.last_heartbeat,
                "battery": d.battery,
                "alive": d.is_alive,
            }
            for d in devices
        ],
        "total_devices": len(devices),
        "total_sessions": session_manager.count(),
        "total_connections": connection_manager.count(),
        "pipeline_plugins": pipeline.plugin_names(),
        "test_mode": pipeline.test_mode,
        "ts": time.time(),
    }


# ─── WebSocket ───

@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket):
    await ws.accept()
    log.info(f"[connect] {ws.client}")

    try:
        while True:
            raw = await ws.receive_text()

            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                await ws.send_text(json.dumps(envelope("error", message="invalid JSON")))
                continue

            # 通过 MessageDispatcher 分发（无 if/else）
            response = await dispatcher.dispatch(ws, data)

            if response:
                await ws.send_text(json.dumps(response))

    except WebSocketDisconnect:
        log.info(f"[disconnect] {ws.client}")
    except Exception as e:
        log.error(f"[error] {ws.client}: {e}")
    finally:
        log.info(f"[cleanup] {ws.client}")


# ─── 启动入口 ───

if __name__ == "__main__":
    import uvicorn
    log.info("=" * 50)
    log.info("  洛克王国 AI Gateway")
    log.info(f"  WebSocket: ws://0.0.0.0:8765/ws")
    log.info(f"  HTTP:      http://0.0.0.0:8765/health")
    log.info(f"  Pipeline:  {pipeline.plugin_names() or '[测试模式]'}")
    log.info("=" * 50)
    uvicorn.run(app, host="0.0.0.0", port=8765, log_level="info")