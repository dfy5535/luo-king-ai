package com.luoking.agent.dispatcher

import com.luoking.agent.models.CoordinateManager
import com.luoking.agent.models.SessionState
import com.luoking.agent.services.InputService
import com.luoking.agent.services.WebSocketClient
import org.json.JSONObject

/**
 * 动作调度器 — 唯一职责：服务器语义动作 → 系统手势
 * 不解析AI、不决策、不截图
 */
object ActionDispatcher {
    fun execute(action: JSONObject, ws: WebSocketClient) {
        val type = action.optString("action_type", "wait")
        val delay = action.optInt("delay_ms", 500).toLong()
        val coord = action.optJSONArray("coordinate")
        val skillIdx = action.optInt("skill_index", -1)
        val swapIdx = action.optInt("swap_index", -1)

        Thread {
            try { Thread.sleep(delay) } catch (_: InterruptedException) {}
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
        }.start()
    }
}