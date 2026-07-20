package com.luoking.agent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luoking.agent.models.SessionState
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var connectBtn: Button
    private lateinit var toggleAccessibilityBtn: Button
    private lateinit var toggleScreenCaptureBtn: Button
    private lateinit var diagnosticsText: TextView
    private var wsClient: WebSocketClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() { updateDiagnostics(); mainHandler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "⚔ 洛克王国 AI 助手"
            textSize = 22f
            setPadding(0, 0, 0, 4)
        })
        layout.addView(TextView(this).apply {
            text = "手机终端执行器"
            textSize = 13f
            setPadding(0, 0, 0, 24)
        })

        // Status
        statusText = TextView(this).apply {
            text = "未连接"
            textSize = 16f
            setMinHeight(80)
            setPadding(0, 0, 0, 16)
        }

        // Buttons
        connectBtn = Button(this).apply {
            text = "🚀 连接服务器"
            setOnClickListener { toggleConnection() }
        }
        toggleAccessibilityBtn = Button(this).apply {
            text = "🔓 无障碍：去设置"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        toggleScreenCaptureBtn = Button(this).apply {
            text = "📸 申请截屏权限"
            setOnClickListener { requestScreenCapture() }
        }

        // Diagnostics
        diagnosticsText = TextView(this).apply {
            text = "等待启动..."
            textSize = 13f
            setPadding(0, 16, 0, 0)
        }

        layout.addView(statusText)
        layout.addView(connectBtn)
        layout.addView(toggleAccessibilityBtn)
        layout.addView(toggleScreenCaptureBtn)
        layout.addView(diagnosticsText)
        scroll.addView(layout)
        setContentView(scroll)

        SessionState.deviceId = "phone_${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)?.takeLast(6) ?: "unknown"}"
        mainHandler.post(updateRunnable)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    private fun toggleConnection() {
        if (wsClient?.isSessionEstablished() == true) {
            wsClient?.disconnect()
            wsClient = null
            connectBtn.text = "🚀 连接服务器"
            statusText.text = "未连接"
        } else {
            connectToServer()
        }
    }

    private fun connectToServer() {
        val serverUrl = SessionState.serverUrl
        statusText.text = "连接中..."
        connectBtn.isEnabled = false

        wsClient = WebSocketClient(
            serverUrl = serverUrl,
            onAction = { action -> executeAction(action) },
            onConnected = { runOnUiThread { statusText.text = "🟡 已连接，等待 session_id..." } },
            onSessionEstablished = { runOnUiThread {
                statusText.text = "🟢 运行中"
                connectBtn.text = "⏹ 断开连接"
                connectBtn.isEnabled = true
                // 启动截图循环
                startScreenshotLoop()
            }},
            onDisconnected = { runOnUiThread {
                statusText.text = "🔴 已断开"
                connectBtn.text = "🚀 连接服务器"
                connectBtn.isEnabled = true
                stopScreenshotLoop()
            }}
        )
        wsClient?.connect()
    }

    private var screenshotLauncherCode = 0
    private fun requestScreenCapture() {
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(pm.createScreenCaptureIntent(), screenshotLauncherCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == screenshotLauncherCode && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("result_code", resultCode)
                putExtra("data", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            ScreenCaptureService.setScreenshotCallback { base64 ->
                if (base64 != null) wsClient?.sendScreenshot(base64)
            }
        }
    }

    private var isScreenshotLoopRunning = false
    private fun startScreenshotLoop() {
        isScreenshotLoopRunning = true
        Thread {
            while (isScreenshotLoopRunning && wsClient?.isSessionEstablished() == true) {
                ScreenCaptureService.requestScreenshot()
                try { Thread.sleep(SessionState.screenshotIntervalMs) } catch (_: InterruptedException) { break }
            }
        }.start()
    }

    private fun stopScreenshotLoop() { isScreenshotLoopRunning = false }

    private fun executeAction(action: JSONObject) {
        val actionType = action.optString("action_type", "wait")
        val delayMs = action.optInt("delay_ms", 500).toLong()
        val coordJson = action.optJSONArray("coordinate")

        Thread {
            try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
            var success = true
            when (actionType) {
                "tap" -> {
                    if (coordJson != null && coordJson.length() >= 2) {
                        success = AccessibilityInputService.tap(coordJson.getInt(0), coordJson.getInt(1))
                    }
                }
                "back" -> success = AccessibilityInputService.back()
                "wait" -> {}
                "skill" -> {
                    val idx = action.optInt("skill_index", -1)
                    val coord = SessionState.getSkillCoord(idx)
                    if (coord != null) success = AccessibilityInputService.tap(coord.first, coord.second)
                }
                "swap" -> {
                    success = AccessibilityInputService.tap(1000, 1000)
                }
            }
            wsClient?.sendActionResult(success)
        }.start()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${AccessibilityInputService::class.java.canonicalName}"
        return try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(":")?.any { it.equals(service, ignoreCase = true) } ?: false
        } catch (_: Exception) { false }
    }

    private fun updateDiagnostics() {
        val sb = StringBuilder()
        sb.appendLine("📱 ${SessionState.deviceId}")
        sb.appendLine("WebSocket: ${if (wsClient?.isSessionEstablished() == true) "🟢" else "🔴"}")
        sb.appendLine("无障碍: ${if (isAccessibilityEnabled()) "🟢" else if (AccessibilityInputService.isRunning()) "🟡" else "🔴"}")
        sb.appendLine("截图: ${if (ScreenCaptureService.isRunning()) "🟢" else "🔴"}")
        sb.appendLine("📸 ${SessionState.totalScreenshots}  🎯 ${SessionState.totalActions}")
        if (SessionState.sessionId.isNotEmpty()) {
            sb.appendLine("🆔 ${SessionState.sessionId.take(8)}")
        }
        if (SessionState.lastError.isNotEmpty()) {
            sb.appendLine("❌ ${SessionState.lastError}")
        }
        diagnosticsText.text = sb.toString()
    }
}

// Coordinate scaling helper
fun SessionState.Companion.getSkillCoord(index: Int): Pair<Int, Int>? {
    val base = mapOf(1 to Pair(250, 1000), 2 to Pair(650, 1000), 3 to Pair(250, 1100), 4 to Pair(650, 1100))
    val b = base[index] ?: return null
    val sx = SessionState.screenWidth.toFloat() / 1080f
    val sy = SessionState.screenHeight.toFloat() / 1920f
    return Pair((b.first * sx).toInt(), (b.second * sy).toInt())
}