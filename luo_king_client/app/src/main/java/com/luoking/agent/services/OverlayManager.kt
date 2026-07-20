package com.luoking.agent.services

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luoking.agent.models.SessionState

/**
 * OverlayManager — 悬浮弹幕管理器
 * 不是 Service，只是 WindowManager 的 View 管理
 * 由 Activity 控制生命周期
 */
object OverlayManager {
    private var wm: WindowManager? = null
    private var overlayView: ScrollView? = null
    private var thoughtText: TextView? = null
    private var battleStateText: TextView? = null
    private var actionText: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isShowing = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDisplay()
            if (isShowing) handler.postDelayed(this, 500)
        }
    }

    fun show(activity: Activity) {
        if (isShowing) return
        isShowing = true
        wm = activity.getSystemService(Activity.WINDOW_SERVICE) as WindowManager

        val scroll = ScrollView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.argb(180, 0, 0, 0))
        }

        layout.addView(TextView(activity).apply {
            text = "⚔ AI 思维弹幕"
            textSize = 14f; setTextColor(Color.WHITE); setPadding(0, 0, 0, 4)
        })

        thoughtText = TextView(activity).apply {
            text = "等待中..."; textSize = 12f; setTextColor(Color.CYAN); setPadding(0, 0, 0, 2)
        }
        layout.addView(thoughtText!!)

        layout.addView(TextView(activity).apply {
            text = "─".repeat(20); textSize = 8f; setTextColor(Color.GRAY)
        })

        battleStateText = TextView(activity).apply {
            text = ""; textSize = 10f; setTextColor(Color.YELLOW); setPadding(0, 2, 0, 2)
        }
        layout.addView(battleStateText!!)

        layout.addView(TextView(activity).apply {
            text = "─".repeat(20); textSize = 8f; setTextColor(Color.GRAY)
        })

        actionText = TextView(activity).apply {
            text = ""; textSize = 12f; setTextColor(Color.GREEN); setPadding(0, 2, 0, 0)
        }
        layout.addView(actionText!!)

        scroll.addView(layout)

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
            y = 120
        }

        try {
            wm?.addView(scroll, params)
            overlayView = scroll
            handler.post(updateRunnable)
        } catch (e: Exception) {
            SessionState.lastError = "HUD overlay failed: ${e.message}"
            isShowing = false
        }
    }

    fun hide() {
        isShowing = false
        handler.removeCallbacks(updateRunnable)
        overlayView?.let { v ->
            try { wm?.removeView(v) } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun updateDisplay() {
        thoughtText?.text = "💭 ${SessionState.currentThought.ifEmpty { "等待中..." }}"
        if (SessionState.currentBattleState.isNotEmpty()) {
            try {
                val json = org.json.JSONObject(SessionState.currentBattleState)
                val sb = StringBuilder()
                if (json.has("enemy_name")) sb.appendLine("敌方: ${json.getString("enemy_name")} HP:${json.optInt("enemy_hp", 0)}%")
                if (json.has("my_name")) sb.appendLine("我方: ${json.getString("my_name")} HP:${json.optInt("my_hp", 0)}%")
                battleStateText?.text = sb.toString()
            } catch (_: Exception) {
                battleStateText?.text = ""
            }
        } else {
            battleStateText?.text = ""
        }
        actionText?.text = "🎯 动作: ${SessionState.lastActionType.ifEmpty { "—" }}  📸${SessionState.totalScreenshots} 📤${SessionState.totalUploads} 🎯${SessionState.totalActions}"
    }
}