"""
最小 WebSocket 测试服务器
使用 FastAPI + Uvicorn
唯一职责：验证 APK 的 heartbeat → upload → action → action_result 闭环
"""
import uuid
import json
import logging
from fastapi import FastAPI, WebSocket, WebSocketDisconnect

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("test_server")

app = FastAPI(title="洛克王国 AI 测试服务器")

sessions = {}
total_uploads = 0
total_actions = 0


@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket):
    global total_uploads, total_actions
    await ws.accept()
    log.info(f"[connect] {ws.client}")

    try:
        while True:
            raw = await ws.receive_text()
            data = json.loads(raw)
            msg_type = data.get("type", "")

            if msg_type == "heartbeat":
                device_id = data.get("device_id", "unknown")
                sid = uuid.uuid4().hex[:12]
                sessions[device_id] = sid
                log.info(f"[heartbeat] device={device_id} session={sid}")
                await ws.send_text(json.dumps({
                    "type": "heartbeat_ack",
                    "session_id": sid
                }))

            elif msg_type == "upload":
                device_id = data.get("device_id", "unknown")
                sid = data.get("session_id", "")
                expected = sessions.get(device_id)
                if not expected or sid != expected:
                    log.warning(f"[upload] bad session: device={device_id} got={sid} expected={expected}")
                    await ws.send_text(json.dumps({"type": "error", "message": "invalid session_id"}))
                    continue
                total_uploads += 1
                img_len = len(data.get("image", ""))
                log.info(f"[upload] #{total_uploads} device={device_id} image={img_len}B")
                total_actions += 1
                await ws.send_text(json.dumps({
                    "type": "action",
                    "action_type": "tap",
                    "coordinate": [540, 960],
                    "delay_ms": 500,
                    "session_id": sid
                }))

            elif msg_type == "action_result":
                device_id = data.get("device_id", "unknown")
                success = data.get("success", False)
                log.info(f"[action_result] device={device_id} success={success}")

            else:
                log.warning(f"[unknown] type={msg_type}")

    except WebSocketDisconnect:
        log.info(f"[disconnect] {ws.client}")
    except Exception as e:
        log.error(f"[error] {e}")
    finally:
        log.info(f"[cleanup] {ws.client}")


if __name__ == "__main__":
    import uvicorn
    log.info("🚀 测试服务器启动: ws://0.0.0.0:8765/ws")
    log.info("    收到 upload 后返回固定 tap 动作，不依赖任何 AI")
    uvicorn.run(app, host="0.0.0.0", port=8765, log_level="info")