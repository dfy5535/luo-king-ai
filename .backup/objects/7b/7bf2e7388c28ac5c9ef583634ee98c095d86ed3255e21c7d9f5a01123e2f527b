package com.luoking.agent

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket 客户端 — Stage 1
 * 只做连接和心跳，不上传截图，不执行动作
 */
class WebSocketClient(
    private val serverUrl: String,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onSessionEstablished: (String) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "WSClient"
    }

    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun connect() {
        val request = Request.Builder().url(serverUrl).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 已连接")
                onConnected()
                // 发送首次心跳
                sendHeartbeat()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.optString("type", "")) {
                        "heartbeat_ack" -> {
                            val sid = json.optString("session_id", "")
                            if (sid.isNotEmpty()) {
                                Log.d(TAG, "session_id 已分配: $sid")
                                onSessionEstablished(sid)
                            }
                        }
                        "error" -> {
                            Log.e(TAG, "服务器错误: ${json.optString("message")}")
                            onError(json.optString("message", "未知错误"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "消息解析失败", e)
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "连接关闭: $reason")
                onDisconnected()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "连接失败: ${t.message}")
                onError(t.message ?: "连接失败")
                onDisconnected()
            }
        })
    }

    fun disconnect() {
        ws?.close(1000, "用户断开")
    }

    private fun sendHeartbeat() {
        val json = JSONObject().apply {
            put("type", "heartbeat")
            put("device_id", "test_phone")
            put("ts", System.currentTimeMillis() / 1000)
        }
        ws?.send(json.toString())
    }
}