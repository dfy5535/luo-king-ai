package com.luoking.agent

import android.util.Log
import com.luoking.agent.models.SessionState
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketClient(
    private val serverUrl: String,
    private val onAction: (JSONObject) -> Unit,
    private val onConnected: () -> Unit = {},
    private val onSessionEstablished: () -> Unit = {},
    private val onDisconnected: () -> Unit = {}
) {
    companion object {
        private const val TAG = "WSClient"
        private const val HEARTBEAT_INTERVAL = 5000L
        private const val RECONNECT_DELAY = 3000L
        private const val MAX_RECONNECT_DELAY = 30000L
    }

    private enum class State { DISCONNECTED, CONNECTING, CONNECTED, SESSION_ESTABLISHED }
    private var state = State.DISCONNECTED
    private var ws: WebSocket? = null
    private var heartbeatThread: Thread? = null
    private var reconnectAttempts = 0
    private var shouldRun = true

    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isSessionEstablished(): Boolean = state == State.SESSION_ESTABLISHED

    fun connect() {
        shouldRun = true
        state = State.CONNECTING
        SessionState.sessionId = ""
        reconnectAttempts = 0

        val request = Request.Builder().url(serverUrl).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                state = State.CONNECTED
                SessionState.isConnected = true
                Log.d(TAG, "WebSocket 已连接: $serverUrl")
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
                                Log.d(TAG, "session_id 已分配: $sid")
                                onSessionEstablished()
                                startHeartbeatLoop()
                            }
                        }
                        "action" -> {
                            if (state < State.SESSION_ESTABLISHED) return
                            SessionState.totalActions++
                            onAction(json)
                        }
                        "error" -> {
                            SessionState.lastError = json.optString("message", "")
                            Log.e(TAG, "服务器错误: ${SessionState.lastError}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "消息解析失败", e)
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                cleanup()
                onDisconnected()
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                cleanup()
                onDisconnected()
                if (shouldRun) scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        shouldRun = false
        cleanup()
        ws?.close(1000, "用户断开")
    }

    private fun cleanup() {
        heartbeatThread?.interrupt()
        heartbeatThread = null
        state = State.DISCONNECTED
        SessionState.isConnected = false
        SessionState.sessionId = ""
    }

    fun sendHeartbeat() {
        if (state < State.CONNECTED) return
        val json = JSONObject().apply {
            put("type", "heartbeat")
            put("device_id", SessionState.deviceId)
            put("phase", SessionState.phase)
            put("battery", 0)
            put("ts", System.currentTimeMillis() / 1000)
            if (state >= State.SESSION_ESTABLISHED) {
                put("session_id", SessionState.sessionId)
            }
        }
        ws?.send(json.toString())
    }

    fun sendScreenshot(base64: String) {
        if (state < State.SESSION_ESTABLISHED) return
        val sid = SessionState.sessionId
        if (sid.isEmpty()) return
        ws?.send(JSONObject().apply {
            put("type", "upload")
            put("device_id", SessionState.deviceId)
            put("session_id", sid)
            put("image", base64)
            put("ts", System.currentTimeMillis() / 1000)
        }.toString())
    }

    fun sendActionResult(success: Boolean) {
        if (state < State.SESSION_ESTABLISHED) return
        val sid = SessionState.sessionId
        if (sid.isEmpty()) return
        ws?.send(JSONObject().apply {
            put("type", "action_result")
            put("device_id", SessionState.deviceId)
            put("session_id", sid)
            put("success", success)
            put("ts", System.currentTimeMillis() / 1000)
        }.toString())
    }

    private fun startHeartbeatLoop() {
        heartbeatThread?.interrupt()
        heartbeatThread = Thread {
            while (!Thread.currentThread().isInterrupted && shouldRun) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL)
                    if (state >= State.CONNECTED) sendHeartbeat()
                } catch (_: InterruptedException) { break }
            }
        }.also { it.start() }
    }

    private fun scheduleReconnect() {
        Thread {
            reconnectAttempts++
            val delay = minOf(
                RECONNECT_DELAY * (1 shl (reconnectAttempts - 1)),
                MAX_RECONNECT_DELAY
            )
            try {
                Thread.sleep(delay)
                if (shouldRun) connect()
            } catch (_: InterruptedException) {}
        }.start()
    }
}