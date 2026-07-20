package com.luoking.agent.dispatcher

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.luoking.agent.managers.PermissionManager
import com.luoking.agent.models.SessionState
import com.luoking.agent.services.CaptureService
import com.luoking.agent.services.OverlayManager
import com.luoking.agent.services.WebSocketClient

/**
 * 服务控制器 — 统一管理所有服务的启停
 */
object ServiceController {
    private var wsClient: WebSocketClient? = null
    private var screenshotThread: Thread? = null
    private var isRunning = false

    fun start(activity: Activity, onStatus: (String) -> Unit) {
        if (isRunning) return
        isRunning = true
        SessionState.isRunning = true
        onStatus("连接中...")
        SessionState.addLog("Starting...")

        wsClient = WebSocketClient(
            onAction = { action -> ActionDispatcher.execute(action, wsClient!!) },
            onConnected = { activity.runOnUiThread { onStatus("已连接，等待 session_id...") } },
            onSessionEstablished = {
                activity.runOnUiThread {
                    onStatus("运行中")
                    OverlayManager.show(activity)
                    startScreenshotLoop()
                }
            },
            onDisconnected = {
                activity.runOnUiThread { onStatus("已断开") }
                stopScreenshotLoop()
            }
        )
        wsClient?.connect()
    }

    fun stop() {
        isRunning = false
        SessionState.isRunning = false
        stopScreenshotLoop()
        wsClient?.disconnect()
        wsClient = null
        OverlayManager.hide()
        SessionState.addLog("Stopped")
    }

    fun startCaptureService(activity: Activity, resultCode: Int, data: Intent) {
        CaptureService.setCallback { base64 ->
            if (base64 != null) {
                SessionState.totalScreenshots++
                wsClient?.sendUpload(base64)
            } else {
                SessionState.lastError = "截图失败"
                SessionState.addLog("Screenshot failed")
            }
        }
        val intent = Intent(activity, CaptureService::class.java).apply {
            putExtra("result_code", resultCode); putExtra("data", data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            activity.startForegroundService(intent)
        else
            activity.startService(intent)
        SessionState.addLog("Capture service started")
    }

    private fun startScreenshotLoop() {
        stopScreenshotLoop()
        if (!CaptureService.isRunning()) {
            SessionState.addLog("⚠ CaptureService not running, screenshot loop delayed")
        }
        screenshotThread = Thread {
            while (isRunning && wsClient?.isEstablished() == true) {
                if (CaptureService.isRunning()) {
                    CaptureService.requestScreenshot()
                } else {
                    SessionState.lastError = "截图服务未运行"
                }
                try { Thread.sleep(1500) } catch (_: InterruptedException) { break }
            }
        }.also { it.start() }
        SessionState.addLog("Screenshot loop started")
    }

    private fun stopScreenshotLoop() {
        screenshotThread?.interrupt(); screenshotThread = null
    }
}