package com.luoking.agent.services

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luoking.agent.models.SessionState

/**
 * OverlayHUD — 悬浮弹幕系统
 * 显示 AI 的内心独白、当前状态、执行动作
 * 使用 TYPE_APPLICATION_OVERLAY（Android 8+ 无需额外权限）
 */
class OverlayHUD : Service() {
    companion object {
        private var instance: OverlayHUD? = null
        fun isRunning(): Boolean = instance != null
        fun start(context: android.content.Context) {
            val intent = Intent(context, OverlayHUD::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }
        fun stop() { instance?.stopSelf() }
    }

    private lateinit var wm: WindowManager
    private lateinit var overlayView: LinearLayout
    private lateinit var thoughtText: TextView
    private lateinit var battleStateText: TextView
    private lateinit var actionText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDisplay()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // 创建悬浮窗布局
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.argb(180, 0, 0, 0))
        }

        // 标题
        overlayView.addView(TextView(this).apply {
            text = "⚔ AI 思维弹幕"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 4)
        })

        // 当前思维
        overlayView.addView(TextView(this).apply {
            text = "等待中..."
            textSize = 12f
            setTextColor(Color.CYAN)
            setPadding(0, 0, 0, 2)
        }.also { thoughtText = it })

        // 分割线
        overlayView.addView(TextView(this).apply {
            text = "─".repeat(20)
            textSize = 8f
            setTextColor(Color.GRAY)
        })

        // 战斗状态
        battleStateText = TextView(this).apply {
            text = ""
            textSize = 10f
            setTextColor(Color.YELLOW)
            setPadding(0, 2, 0, 2)
        }
        overlayView.addView(battleStateText)

        // 分割线
        overlayView.addView(TextView(this).apply {
            text = "─".repeat(20)
            textSize = 8f
            setTextColor(Color.GRAY)
        })

        // 动作
        actionText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.GREEN)
            setPadding(0, 2, 0, 0)
        }
        overlayView.addView(actionText)

        scroll.addView(overlayView)

        // 参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120 // 距顶部偏移
        }

        wm.addView(scroll, params)
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = android.app.NotificationChannel(
                "luo_king_hud", "AI HUD",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .createNotificationChannel(ch)
            startForeground(2, android.app.Notification.Builder(this, "luo_king_hud")
                .setContentTitle("AI 弹幕运行中")
                .setContentText("正在观察战场...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .build())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(updateRunnable)
        try { wm.removeView(overlayView.parent as? android.view.View ?: overlayView) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateDisplay() {
        thoughtText.text = "💭 ${SessionState.currentThought.ifEmpty { "等待中..." }}"
        if (SessionState.currentBattleState.isNotEmpty()) {
            try {
                val json = org.json.JSONObject(SessionState.currentBattleState)
                val sb = StringBuilder()
                if (json.has("enemy_name")) sb.appendLine("敌方: ${json.getString("enemy_name")} HP:${json.optInt("enemy_hp", 0)}%")
                if (json.has("my_name")) sb.appendLine("我方: ${json.getString("my_name")} HP:${json.optInt("my_hp", 0)}%")
                battleStateText.text = sb.toString()
            } catch (_: Exception) {
                battleStateText.text = ""
            }
        } else {
            battleStateText.text = ""
        }
        actionText.text = "🎯 动作: ${SessionState.lastActionType.ifEmpty { "—" }}  📸${SessionState.totalScreenshots} 📤${SessionState.totalUploads} 🎯${SessionState.totalActions}"
    }
}