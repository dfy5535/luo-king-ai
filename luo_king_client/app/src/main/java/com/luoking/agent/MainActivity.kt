package com.luoking.agent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luoking.agent.dispatcher.ServiceController
import com.luoking.agent.managers.ConfigManager
import com.luoking.agent.managers.PermissionManager
import com.luoking.agent.models.SessionState
import com.luoking.agent.services.CaptureService
import com.luoking.agent.services.InputService
import com.luoking.agent.services.WebSocketClient
import org.json.JSONObject

/**
 * 主界面 — 完整诊断面板
 * 每秒刷新，显示所有内部状态
 */
class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var panelText: TextView
    private lateinit var logText: TextView
    private lateinit var startBtn: Button
    private lateinit var accessibilityBtn: Button
    private lateinit var captureBtn: Button
    private lateinit var testConnectBtn: Button
    private lateinit var testTapBtn: Button
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val diagRunnable = object : Runnable {
        override fun run() { updatePanel(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigManager.init(this)
        SessionState.deviceId = "phone_${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)?.takeLast(6) ?: "unknown"}"

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
        }

        // 标题
        layout.addView(TextView(this).apply {
            text = "⚔ 洛克王国 AI 助手"; textSize = 22f; setPadding(0, 0, 0, 4)
        })

        // 状态行
        statusText = TextView(this).apply {
            text = "就绪"; textSize = 16f; setMinHeight(40); setPadding(0, 0, 0, 16)
        }
        layout.addView(statusText)

        // 操作按钮行
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startBtn = Button(this).apply { text = "🚀 启动"; setOnClickListener { toggleStart() } }
        accessibilityBtn = Button(this).apply { text = "🔓 无障碍"; setOnClickListener {
            PermissionManager.requestAccessibility(this@MainActivity)
        } }
        captureBtn = Button(this).apply { text = "📸 截屏"; setOnClickListener {
            startActivityForResult(PermissionManager.requestScreenCapture(this@MainActivity), CAPTURE_REQ)
        } }
        btnRow.addView(startBtn); btnRow.addView(accessibilityBtn); btnRow.addView(captureBtn)
        layout.addView(btnRow)

        // 测试按钮行
        val testRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        testConnectBtn = Button(this).apply { text = "🔍 测试连接"; setOnClickListener { testConnection() } }
        testTapBtn = Button(this).apply { text = "👆 测试点击"; setOnClickListener { testTap() } }
        testRow.addView(testConnectBtn); testRow.addView(testTapBtn)
        layout.addView(testRow)

        // 诊断面板
        panelText = TextView(this).apply {
            textSize = 12f; setPadding(0, 12, 0, 0)
            setMinHeight(200)
        }
        layout.addView(panelText)

        // 日志窗口
        layout.addView(TextView(this).apply {
            text = "── 实时日志 ──"; textSize = 13f; setPadding(0, 8, 0, 4)
        })
        logText = TextView(this).apply {
            textSize = 10f; setPadding(0, 0, 0, 0); setMinHeight(120)
        }
        layout.addView(logText)

        scroll.addView(layout); setContentView(scroll)
        handler.post(diagRunnable)
    }

    override fun onDestroy() { handler.removeCallbacks(diagRunnable); super.onDestroy() }

    private fun toggleStart() {
        if (isRunning) {
            ServiceController.stop(); isRunning = false
            startBtn.text = "🚀 启动"; statusText.text = "已停止"
        } else {
            val missing = PermissionManager.checkAll(this)
            if (missing.isNotEmpty()) {
                statusText.text = "❌ 缺少权限: ${missing.joinToString("、")}"
                return
            }
            isRunning = true
            ServiceController.start(this) { status -> runOnUiThread { statusText.text = status } }
            startBtn.text = "⏹ 停止"
        }
    }

    private fun testConnection() {
        val ws = WebSocketClient(
            onAction = {},
            onConnected = { runOnUiThread { statusText.text = "✅ 测试连接成功" } },
            onSessionEstablished = { runOnUiThread { statusText.text = "✅ 测试会话建立成功" } },
            onDisconnected = { runOnUiThread { statusText.text = "❌ 测试连接断开" } }
        )
        ws.connect()
        statusText.text = "🔍 测试连接中..."
    }

    private fun testTap() {
        if (!InputService.isRunning()) {
            statusText.text = "❌ 无障碍服务未运行"
            return
        }
        val ok = InputService.tap(540, 960)
        statusText.text = if (ok) "✅ 测试点击成功 (540,960)" else "❌ 测试点击失败"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAPTURE_REQ && resultCode == RESULT_OK && data != null) {
            PermissionManager.projectionGranted = true
            ServiceController.startCaptureService(this, resultCode, data)
            statusText.text = "截屏已授权"
        }
    }

    private fun updatePanel() {
        // 诊断面板
        val sb = StringBuilder()
        sb.appendLine("╔══════════════════════════")
        sb.appendLine("║ 📱 ${SessionState.deviceId}")
        val sid = SessionState.sessionId
        if (sid.isNotEmpty()) sb.appendLine("║ 🆔 ${sid.take(8)}...")
        sb.appendLine("╠══════════════════════════")

        // 网络状态
        sb.append("║ WebSocket: ")
        sb.appendLine(if (SessionState.isConnected) "🟢 Connected" else "🔴 Disconnected")
        sb.appendLine("║ Server: ${ConfigManager.serverUrl}")

        // 心跳
        sb.appendLine("║ Heartbeat: ↑${SessionState.heartbeatSent} ↓${SessionState.heartbeatReceived}")
        if (SessionState.pingMs > 0) sb.appendLine("║ Latency: ${SessionState.pingMs}ms")

        // 截图 & 上传
        sb.appendLine("║ Screenshot: ${SessionState.totalScreenshots}")
        sb.appendLine("║ Upload: ${SessionState.totalUploads}")
        if (SessionState.lastUploadSize > 0)
            sb.appendLine("║ Last upload: ${SessionState.lastUploadSize / 1024}KB")

        // 动作
        sb.appendLine("║ Action: ${SessionState.totalActions}")
        if (SessionState.lastActionType.isNotEmpty())
            sb.appendLine("║ Last: ${SessionState.lastActionType}")

        // 手势
        sb.appendLine("║ Gesture: ${SessionState.totalGestures}")
        sb.appendLine("║   ✓${SessionState.gestureSuccess}  ✗${SessionState.gestureFail}")

        // 无障碍 & 截图服务
        sb.append("║ Accessibility: ")
        sb.appendLine(if (PermissionManager.isAccessibilityEnabled(this)) "🟢" else "🔴")
        sb.append("║ Capture: ")
        sb.appendLine(if (CaptureService.isRunning()) "🟢" else "🔴")

        // 错误
        if (SessionState.lastError.isNotEmpty()) {
            sb.appendLine("╠══════════════════════════")
            sb.appendLine("║ ❌ ${SessionState.lastError}")
        }

        sb.appendLine("╚══════════════════════════")
        panelText.text = sb.toString()

        // 日志窗口（只显示最近 15 条）
        val logs = SessionState.logs()
        val recent = if (logs.size > 15) logs.subList(logs.size - 15, logs.size) else logs
        logText.text = recent.joinToString("\n")
    }

    companion object { private const val CAPTURE_REQ = 1001 }
}