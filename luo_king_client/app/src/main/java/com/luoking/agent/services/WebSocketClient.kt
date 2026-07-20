package com.luoking.agent.services

import android.util.Log
import com.luoking.agent.dispatcher.ActionDispatcher
import com.luoking.agent.managers.ConfigManager
import com.luoking.agent.models.SessionState
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket 客户端 — 唯一职责：连接、收发、心跳、重连
 * 所有事件写日志到 SessionState
 */
class WebSocketClient(
    private val onAction: (JSONObject) -> Unit,
    private val onConnected: () -> Unit = {},
    private val onSessionEstablished: () -> Unit = {},
    private val onDisconnected: () -> Unit = {}
) {
    companion object {
        private const val TAG = "WSClient"
    }

    private enum class State { DISCONNECTED, CONNECTING, CONNECTED, SESSION_ESTABLISHED }
    private var state = State.DISCONNECTED
    private var ws: WebSocket? = null
    private var heartbeatThread: Thread? = null
    private var shouldRun = true

    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    fun isEstablished(): Boolean = state == State.SESSION_ESTABLISHED

    fun connect() {
        if (state != State.DISCONNECTED) {
            SessionState.addLog("Already connecting, skipping")
            return
        }
        shouldRun = true
        state = State.CONNECTING
        SessionState.reset()
        SessionState.addLog("Connecting to ${ConfigManager.serverUrl}...")
        val req = Request.Builder().url(ConfigManager.serverUrl).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                state = State.CONNECTED
                SessionState.isConnected = true
                SessionState.addLog("Connected")
                onConnected()
                sendHeartbeat()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.optString("type", "")) {
                        "heartbeat_ack" -> {
                            val sid = json.optString("session_id", "")
                            if (sid.isNotEmpty() && state < State.SESSION_ESTABLISHED) {
                                state = State.SESSION_ESTABLISHED
                                SessionState.sessionId = sid
                                SessionState.heartbeatReceived++
                                SessionState.lastHeartbeatAckTime = System.currentTimeMillis()
                                Log.d(TAG, "session_id: $sid")
                                SessionState.addLog("Heartbeat ACK, session=${sid.take(8)}")
                                onSessionEstablished()
                                startHeartbeat()
                            } else if (sid.isNotEmpty()) {
                                SessionState.heartbeatReceived++
                                SessionState.lastHeartbeatAckTime = System.currentTimeMillis()
                            }
                        }
                        "decision" -> {
                            // 结构化决策：think + decision + execute
                            if (state < State.SESSION_ESTABLISHED) return
                            SessionState.totalActions++
                            SessionState.lastActionTime = System.currentTimeMillis()
                            SessionState.addLog("📊 Decision received")
                            ActionDispatcher.enqueueDecision(json, this@WebSocketClient)
                        }
                        "action" -> {
                            if (state < State.SESSION_ESTABLISHED) return
                            SessionState.totalActions++
                            SessionState.lastActionTime = System.currentTimeMillis()
                            SessionState.lastActionType = json.optString("action_type", "?")
                            SessionState.addLog("Action: ${SessionState.lastActionType}")
                            onAction(json)
                        }
                        "thought" -> {
                            val text = json.optString("text", "")
                            if (text.isNotEmpty()) {
                                SessionState.addThought(text)
                                SessionState.addLog("💭 $text")
                            }
                        }
                        "battle_state" -> {
                            val state = json.optJSONObject("state")
                            if (state != null) {
                                SessionState.currentBattleState = state.toString()
                                SessionState.addLog("📊 BattleState updated")
                            }
                        }
                        "error" -> {
                            val msg = json.optString("message", "")
                            SessionState.lastError = msg
                            SessionState.addLog("Server error: $msg")
                            Log.e(TAG, "服务器错误: $msg")
                        }
                    }
                } catch (e: Exception) {
                    SessionState.lastError = "消息解析失败: ${e.message}"
                    SessionState.addLog("Parse error: ${e.message}")
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                SessionState.lastError = "连接关闭: $reason (code=$code)"
                SessionState.addLog("Closed: $reason (code=$code)")
                cleanup(); onDisconnected(); if (shouldRun) reconnect()
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val msg = t.message ?: "未知连接错误"
                SessionState.lastError = msg
                SessionState.addLog("Failure: $msg")
                cleanup(); onDisconnected(); if (shouldRun) reconnect()
            }
        })
    }

    fun disconnect() {
        shouldRun = false
        cleanup()
        ws?.close(1000, "用户断开")
        SessionState.addLog("Disconnected by user")
    }

    private fun cleanup() {
        heartbeatThread?.interrupt(); heartbeatThread = null
        state = State.DISCONNECTED; SessionState.reset()
    }

    fun sendHeartbeat() {
        if (state < State.CONNECTED) return
        val now = System.currentTimeMillis()
        SessionState.lastHeartbeatTime = now
        SessionState.heartbeatSent++
        ws?.send(JSONObject().apply {
            put("type", "heartbeat"); put("device_id", SessionState.deviceId)
            put("ts", now / 1000)
            if (state >= State.SESSION_ESTABLISHED) put("session_id", SessionState.sessionId)
        }.toString())
    }

    fun sendUpload(base64: String) {
        if (state < State.SESSION_ESTABLISHED) return
        val sid = SessionState.sessionId; if (sid.isEmpty()) return
        val now = System.currentTimeMillis()
        SessionState.lastUploadTime = now
        SessionState.totalUploads++
        SessionState.lastUploadSize = base64.length.toLong()
        SessionState.addLog("Upload ${base64.length}B")
        ws?.send(JSONObject().apply {
            put("type", "upload"); put("device_id", SessionState.deviceId)
            put("session_id", sid); put("image", base64)
            put("ts", now / 1000)
        }.toString())
    }

    fun sendActionResult(success: Boolean) {
        if (state < State.SESSION_ESTABLISHED) return
        val sid = SessionState.sessionId; if (sid.isEmpty()) return
        ws?.send(JSONObject().apply {
            put("type", "action_result"); put("device_id", SessionState.deviceId)
            put("session_id", sid); put("success", success)
            put("ts", System.currentTimeMillis() / 1000)
        }.toString())
    }

    private fun startHeartbeat() {
        heartbeatThread?.interrupt()
        heartbeatThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try { Thread.sleep(ConfigManager.heartbeatInterval); sendHeartbeat() }
                catch (_: InterruptedException) { break }
            }
        }.also { it.start() }
    }

    private fun reconnect() {
        Thread {
            SessionState.addLog("Reconnecting in ${ConfigManager.reconnectDelay}ms...")
            try { Thread.sleep(ConfigManager.reconnectDelay); if (shouldRun) connect() }
            catch (_: InterruptedException) {}
        }.start()
    }
}