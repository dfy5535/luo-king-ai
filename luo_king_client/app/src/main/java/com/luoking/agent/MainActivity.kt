package com.luoking.agent

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var connectBtn: Button
    private var wsClient: WebSocketClient? = null
    private var sessionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 根布局用 ScrollView，确保内容不会被截断
        val scroll = ScrollView(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "⚔ 洛克王国 AI 助手"
            textSize = 22f
            setTextColor(Color.BLACK)
        }

        val subtitle = TextView(this).apply {
            text = "Stage 1 — WebSocket 连接测试"
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(0, 4, 0, 24)
        }

        statusText = TextView(this).apply {
            text = "未连接"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(0, 16, 0, 16)
            setMinHeight(120)
        }

        connectBtn = Button(this).apply {
            text = "连接服务器"
            setOnClickListener {
                if (wsClient == null) connect() else disconnect()
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(statusText)
        layout.addView(connectBtn)
        scroll.addView(layout)
        setContentView(scroll)
    }

    private fun connect() {
        val serverUrl = "ws://193.112.187.72:8765"
        updateStatus("连接中...", Color.DKGRAY)
        connectBtn.isEnabled = false

        wsClient = WebSocketClient(
            serverUrl = serverUrl,
            onConnected = {
                runOnUiThread { updateStatus("🟢 WebSocket 已连接，等待 session_id...", Color.DKGRAY) }
            },
            onSessionEstablished = { sid ->
                sessionId = sid
                runOnUiThread {
                    updateStatus("🟢 会话已建立\n🆔 $sid", Color.DKGRAY)
                    connectBtn.text = "断开连接"
                    connectBtn.isEnabled = true
                }
            },
            onDisconnected = {
                runOnUiThread {
                    updateStatus("🔴 已断开", Color.RED)
                    connectBtn.text = "连接服务器"
                    connectBtn.isEnabled = true
                }
            },
            onError = { msg ->
                runOnUiThread {
                    updateStatus("❌ 连接失败\n$msg\n\n请检查服务器是否已启动", Color.RED)
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
        updateStatus("未连接", Color.DKGRAY)
        connectBtn.text = "连接服务器"
    }

    private fun updateStatus(text: String, color: Int) {
        statusText.text = text
        statusText.setTextColor(color)
    }
}