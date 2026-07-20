package com.luoking.agent

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var connectBtn: Button
    private var wsClient: WebSocketClient? = null
    private var sessionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "⚔ 洛克王国 AI 助手"
            textSize = 22f
        }

        statusText = TextView(this).apply {
            text = "未连接"
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }

        connectBtn = Button(this).apply {
            text = "连接服务器"
            setOnClickListener {
                if (wsClient == null) {
                    connect()
                } else {
                    disconnect()
                }
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(connectBtn)
        setContentView(layout)
    }

    private fun connect() {
        val serverUrl = "ws://193.112.187.72:8765"
        statusText.text = "连接中..."
        connectBtn.isEnabled = false

        wsClient = WebSocketClient(
            serverUrl = serverUrl,
            onConnected = {
                runOnUiThread {
                    statusText.text = "🟢 WebSocket 已连接，等待 session_id..."
                }
            },
            onSessionEstablished = { sid ->
                sessionId = sid
                runOnUiThread {
                    statusText.text = "🟢 会话已建立\n🆔 $sid"
                    connectBtn.text = "断开连接"
                    connectBtn.isEnabled = true
                }
            },
            onDisconnected = {
                runOnUiThread {
                    statusText.text = "🔴 已断开"
                    connectBtn.text = "连接服务器"
                    connectBtn.isEnabled = true
                }
            },
            onError = { msg ->
                runOnUiThread {
                    statusText.text = "❌ $msg"
                    connectBtn.isEnabled = true
                }
            }
        )
        wsClient?.connect()
    }

    private fun disconnect() {
        wsClient?.disconnect()
        wsClient = null
        sessionId = ""
        statusText.text = "未连接"
        connectBtn.text = "连接服务器"
    }
}