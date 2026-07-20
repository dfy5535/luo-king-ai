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
 * OverlayManager — 三栏 HUD 悬浮弹幕
 * 双轨模式：AI 解说 + AI 执行
 *
 * ┌────────────────────────────────┐
 * │ 👀 看见                        │
 * │ 敌: 冰龙王 HP:82%              │
 * │ 我: 烈火战神 HP:61%            │
 * ├────────────────────────────────┤
 * │ 🧠 思考                        │
 * │ 阶段: 分析敌方                 │
 * │ 理由: 敌方速度更快             │
 * │ 决策: swap → 圣光迪莫          │
 * ├────────────────────────────────┤
 * │ 👉 动作                        │
 * │ 即将执行: tap (3...)           │
 * │ ✅ 已执行                      │
 * └────────────────────────────────┘
 */
object OverlayManager {
    private var wm: WindowManager? = null
    private var overlayView: ScrollView? = null
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())

    // 三栏 TextView
    private var seeText: TextView? = null       // 👀 看见
    private var thinkText: TextView? = null     // 🧠 思考
    private var actionText: TextView? = null    // 👉 动作

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDisplay()
            if (isShowing) handler.postDelayed(this, 200)  // 200ms 刷新，倒计时更流畅
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
            setPadding(8, 6, 8, 6)
            setBackgroundColor(Color.argb(200, 0, 0, 0))
        }

        // ─── 标题行 ───
        layout.addView(TextView(activity).apply {
            text = "🎮 洛克王国 AI  — 双轨模式"
            textSize = 11f; setTextColor(Color.WHITE); setPadding(0, 0, 0, 4)
        })

        // ─── 分隔线 ───
        fun divider() = TextView(activity).apply {
            text = "─".repeat(30); textSize = 6f; setTextColor(Color.GRAY)
        }

        // ─── 面板 1: 👀 看见 ───
        seeText = TextView(activity).apply {
            textSize = 10f; setTextColor(Color.CYAN); setPadding(0, 2, 0, 2)
            setMinHeight(30)
        }
        layout.addView(seeText!!)
        layout.addView(divider())

        // ─── 面板 2: 🧠 思考 ───
        thinkText = TextView(activity).apply {
            textSize = 10f; setTextColor(Color.YELLOW); setPadding(0, 2, 0, 2)
            setMinHeight(40)
        }
        layout.addView(thinkText!!)
        layout.addView(divider())

        // ─── 面板 3: 👉 动作 ───
        actionText = TextView(activity).apply {
            textSize = 11f; setTextColor(Color.GREEN); setPadding(0, 2, 0, 0)
            setMinHeight(24)
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
            y = 80
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
        val seeSb = StringBuilder()
        seeSb.appendLine("👀 看见")
        seeSb.append("敌: ${SessionState.hudEnemyName} ")
        if (SessionState.hudEnemyHp > 0) seeSb.append("HP:${SessionState.hudEnemyHp}%")
        seeSb.appendLine()
        seeSb.append("我: ${SessionState.hudMyName} ")
        if (SessionState.hudMyHp > 0) seeSb.append("HP:${SessionState.hudMyHp}%")
        if (SessionState.hudWeather.isNotEmpty()) seeSb.append(" ☀${SessionState.hudWeather}")
        seeText?.text = seeSb.toString()

        // 面板 2：思考
        val thinkSb = StringBuilder()
        thinkSb.appendLine("🧠 思考")
        thinkSb.appendLine("阶段: ${SessionState.hudThinkStage}")
        if (SessionState.hudThinkReason.isNotEmpty()) {
            thinkSb.appendLine("理由: ${SessionState.hudThinkReason}")
        }
        if (SessionState.hudDecisionAction.isNotEmpty()) {
            thinkSb.append("决策: ${SessionState.hudDecisionAction}")
            if (SessionState.hudDecisionTarget.isNotEmpty()) {
                thinkSb.append(" → ${SessionState.hudDecisionTarget}")
            }
            thinkSb.appendLine()
            if (SessionState.hudDecisionReason.isNotEmpty()) {
                thinkSb.append("原因: ${SessionState.hudDecisionReason}")
            }
        }
        if (SessionState.hudThinkConfidence > 0) {
            thinkSb.append(" (${(SessionState.hudThinkConfidence * 100).toInt()}%)")
        }
        thinkText?.text = thinkSb.toString()

        // 面板 3：动作
        val actSb = StringBuilder()
        actSb.append("👉 动作")
        if (SessionState.hudActionStage != "等待") {
            actSb.append(" | ${SessionState.hudActionStage}")
            if (SessionState.hudActionCountdown > 0) {
                actSb.append(" (${SessionState.hudActionCountdown}...)")
            }
            if (SessionState.hudExecuteType.isNotEmpty()) {
                actSb.append(" | ${SessionState.hudExecuteType}")
                if (SessionState.hudExecuteX > 0 || SessionState.hudExecuteY > 0) {
                    actSb.append(" (${SessionState.hudExecuteX},${SessionState.hudExecuteY})")
                }
            }
        }
        actionText?.text = actSb.toString()
    }

    fun updateSee(enemyName: String, enemyHp: Int, myName: String, myHp: Int, weather: String) {
        SessionState.hudEnemyName = enemyName
        SessionState.hudEnemyHp = enemyHp
        SessionState.hudMyName = myName
        SessionState.hudMyHp = myHp
        SessionState.hudWeather = weather
    }
}