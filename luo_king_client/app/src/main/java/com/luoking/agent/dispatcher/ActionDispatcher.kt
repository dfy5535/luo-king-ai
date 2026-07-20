package com.luoking.agent.dispatcher

import android.os.Handler
import android.os.Looper
import com.luoking.agent.models.CoordinateManager
import com.luoking.agent.models.SessionState
import com.luoking.agent.services.InputService
import com.luoking.agent.services.WebSocketClient
import org.json.JSONObject

/**
 * 动作调度器 — ActionQueue 双轨模式
 * 收到 decision → HUD 更新 → 倒计时 3-2-1 → 执行点击
 * HUD 和 Gesture 完全分离
 */
object ActionDispatcher {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentQueueItem: ActionQueueItem? = null

    data class ActionQueueItem(
        val title: String,
        val reason: String,
        val execute: () -> Boolean,
        val delayMs: Long
    )

    fun execute(action: JSONObject, ws: WebSocketClient) {
        val type = action.optString("action_type", "wait")
        val delay = action.optInt("delay_ms", 500).toLong()
        val coord = action.optJSONArray("coordinate")
        val skillIdx = action.optInt("skill_index", -1)
        val swapIdx = action.optInt("swap_index", -1)

        // 构建执行闭包
        val execBlock: () -> Boolean = {
            var ok = true
            try {
                SessionState.totalGestures++
                when (type) {
                    "tap" -> {
                        if (coord != null && coord.length() >= 2)
                            ok = InputService.tap(coord.getInt(0), coord.getInt(1))
                    }
                    "skill" -> {
                        val c = CoordinateManager.skill(skillIdx)
                        if (c != null) ok = InputService.tap(c.first, c.second)
                    }
                    "swap" -> {
                        val c = CoordinateManager.swap(swapIdx)
                        if (c != null) ok = InputService.tap(c.first, c.second)
                    }
                    "back" -> ok = InputService.back()
                    "wait" -> {}
                }
            } catch (_: Exception) { ok = false }
            if (ok) SessionState.gestureSuccess++ else SessionState.gestureFail++
            ws.sendActionResult(ok)
            ok
        }

        enqueue(ActionQueueItem(
            title = type,
            reason = SessionState.hudDecisionReason.ifEmpty { "执行动作" },
            execute = execBlock,
            delayMs = delay
        ))
    }

    fun enqueueDecision(decision: JSONObject, ws: WebSocketClient) {
        val think = decision.optJSONObject("think")
        val dec = decision.optJSONObject("decision")
        val execute = decision.optJSONObject("execute")

        // 更新 SessionState HUD 字段
        SessionState.updateDecision(think, dec, execute)

        val executeType = execute?.optString("type", "tap") ?: "tap"
        val delay = execute?.optLong("delay_ms", 800L) ?: 800L
        val coord = execute?.optJSONArray("coordinate")
        val skillIdx = execute?.optInt("skill_index", -1) ?: -1
        val swapIdx = execute?.optInt("swap_index", -1) ?: -1

        val execBlock: () -> Boolean = {
            var ok = true
            try {
                SessionState.totalGestures++
                when (executeType) {
                    "tap" -> {
                        if (coord != null && coord.length() >= 2)
                            ok = InputService.tap(coord.getInt(0), coord.getInt(1))
                    }
                    "skill" -> {
                        val c = CoordinateManager.skill(skillIdx)
                        if (c != null) ok = InputService.tap(c.first, c.second)
                    }
                    "swap" -> {
                        val c = CoordinateManager.swap(swapIdx)
                        if (c != null) ok = InputService.tap(c.first, c.second)
                    }
                    "back" -> ok = InputService.back()
                    "wait" -> {}
                }
            } catch (_: Exception) { ok = false }
            if (ok) SessionState.gestureSuccess++ else SessionState.gestureFail++
            ws.sendActionResult(ok)
            ok
        }

        enqueue(ActionQueueItem(
            title = executeType,
            reason = dec?.optString("reason", "决策执行") ?: "决策执行",
            execute = execBlock,
            delayMs = delay
        ))
    }

    private fun enqueue(item: ActionQueueItem) {
        currentQueueItem = item
        SessionState.hudActionStage = "即将执行"
        SessionState.lastActionType = item.title
        SessionState.totalActions++

        // 倒计时 3-2-1
        countdownAndExecute(item)
    }

    private fun countdownAndExecute(item: ActionQueueItem) {
        SessionState.hudActionCountdown = 3
        SessionState.addLog("⏳ 倒计时 3... ${item.title}")

        mainHandler.postDelayed({
            SessionState.hudActionCountdown = 2
            SessionState.addLog("⏳ 倒计时 2...")
        }, 500)

        mainHandler.postDelayed({
            SessionState.hudActionCountdown = 1
            SessionState.addLog("⏳ 倒计时 1...")
        }, 1000)

        mainHandler.postDelayed({
            SessionState.hudActionCountdown = 0
            SessionState.hudActionStage = "执行中"
            SessionState.addLog("👉 执行: ${item.title}")
            val ok = item.execute()
            SessionState.hudActionStage = if (ok) "✅ 已执行" else "❌ 失败"
            SessionState.addLog(if (ok) "✅ 执行成功" else "❌ 执行失败")
            currentQueueItem = null
        }, 1500 + item.delayMs)
    }

    fun isExecuting(): Boolean = currentQueueItem != null
}