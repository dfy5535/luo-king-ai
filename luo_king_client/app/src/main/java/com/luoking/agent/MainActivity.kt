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
import com.luoking.agent.models.CoordinateManager
import com.luoking.agent.models.SessionState
import com.luoking.agent.services.CaptureService
import com.luoking.agent.services.InputService

/**
 * 主界面 — 控制台，不包含任何业务逻辑
 * 所有操作委托给 ServiceController / PermissionManager / ConfigManager
 */
class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var diagnosticsText: TextView
    private lateinit var startBtn: Button
    private lateinit var accessibilityBtn: Button
    private lateinit var captureBtn: Button
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val diagRunnable = object : Runnable {
        override fun run() { updateDiagnostics(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigManager.init(this)
        SessionState.deviceId = "phone_${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)?.takeLast(6) ?: "unknown"}"

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }

        layout.addView(TextView(this).apply { text = "⚔ 洛克王国 AI 助手"; textSize = 22f; setPadding(0, 0, 0, 4) })
        layout.addView(TextView(this).apply { text = "手机执行器节点"; textSize = 13f; setPadding(0, 0, 0, 24) })

        statusText = TextView(this).apply { text = "就绪"; textSize = 16f; setMinHeight(60); setPadding(0, 0, 0, 16) }
        startBtn = Button(this).apply { text = "🚀 启动"; setOnClickListener { toggleStart() } }
        accessibilityBtn = Button(this).apply { text = "🔓 无障碍设置"; setOnClickListener { PermissionManager.requestAccessibility(this@MainActivity) } }
        captureBtn = Button(this).apply { text = "📸 授权截屏"; setOnClickListener { startActivityForResult(PermissionManager.requestScreenCapture(this@MainActivity), CAPTURE_REQ) } }
        diagnosticsText = TextView(this).apply { text = "等待启动..."; textSize = 13f; setPadding(0, 16, 0, 0) }

        layout.addView(statusText); layout.addView(startBtn)
        layout.addView(accessibilityBtn); layout.addView(captureBtn); layout.addView(diagnosticsText)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAPTURE_REQ && resultCode == RESULT_OK && data != null) {
            PermissionManager.projectionGranted = true
            ServiceController.startCaptureService(this, resultCode, data)
            statusText.text = "截屏已授权"
        }
    }

    private fun updateDiagnostics() {
        val sb = StringBuilder()
        sb.appendLine("📱 ${SessionState.deviceId}")
        sb.appendLine("WebSocket: ${if (SessionState.isConnected) "🟢" else "🔴"}")
        sb.appendLine("无障碍: ${if (PermissionManager.isAccessibilityEnabled(this)) "🟢" else "🔴"}")
        sb.appendLine("截图: ${if (CaptureService.isRunning()) "🟢" else "🔴"}")
        if (SessionState.sessionId.isNotEmpty()) sb.appendLine("🆔 ${SessionState.sessionId.take(8)}")
        sb.appendLine("📸 ${SessionState.totalScreenshots}  🎯 ${SessionState.totalActions}")
        if (SessionState.lastError.isNotEmpty()) sb.appendLine("❌ ${SessionState.lastError}")
        diagnosticsText.text = sb.toString()
    }

    companion object { private const val CAPTURE_REQ = 1001 }
}