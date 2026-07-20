package com.luoking.agent.services

import android.util.Log
import com.luoking.agent.managers.ConfigManager
import com.luoking.agent.models.SessionState
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket 客户端 — 唯一职责：连接、收发、心跳、重连
 * 不能解析AI、不能决定动作、不能截图
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
        shouldRun = true
        state = State.CONNECTING
        SessionState.reset()
        val req = Request.Builder().url(ConfigManager.serverUrl).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                state = State.CONNECTED
                SessionState.isConnected = true
                onConnected()
                sendHeartbeat()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.optString("type", "")) {
                        "heartbeat_ack" -> {
                            val sid = json.optString("session_id", "")
                            if (sid.isNotEmpty()) {
                                state = State.SESSION_ESTABLISHED
                                SessionState.sessionId = sid
                                Log.d(TAG, "session_id: $sid")
                                onSessionEstablished()
                                startHeartbeat()
                            }
                        }
                        "action" -> {
                            if (state < State.SESSION_ESTABLISHED) return
                            SessionState.lastActionTime = System.currentTimeMillis()
                            onAction(json)
                        }
                        "error" -> {
                            SessionState.lastError = json.optString("message", "")
                            Log.e(TAG, "服务器错误: ${SessionState.lastError}")
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) { cleanup(); onDisconnected(); reconnect() }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { cleanup(); onDisconnected(); if (shouldRun) reconnect() }
        })
    }

    fun disconnect() { shouldRun = false; cleanup(); ws?.close(1000, "用户断开") }

    private fun cleanup() {
        heartbeatThread?.interrupt(); heartbeatThread = null
        state = State.DISCONNECTED; SessionState.reset()
    }

    fun sendHeartbeat() {
        if (state < State.CONNECTED) return
        ws?.send(JSONObject().apply {
            put("type", "heartbeat"); put("device_id", SessionState.deviceId)
            put("ts", System.currentTimeMillis() / 1000)
            if (state >= State.SESSION_ESTABLISHED) put("session_id", SessionState.sessionId)
        }.toString())
    }

    fun sendUpload(base64: String) {
        if (state < State.SESSION_ESTABLISHED) return
        val sid = SessionState.sessionId; if (sid.isEmpty()) return
        SessionState.lastUploadTime = System.currentTimeMillis()
        ws?.send(JSONObject().apply {
            put("type", "upload"); put("device_id", SessionState.deviceId)
            put("session_id", sid); put("image", base64)
            put("ts", System.currentTimeMillis() / 1000)
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
            try { Thread.sleep(ConfigManager.reconnectDelay); if (shouldRun) connect() }
            catch (_: InterruptedException) {}
        }.start()
    }
}