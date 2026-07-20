package com.luoking.agent.models

/**
 * 全局状态 — 唯一状态源头
 * 所有模块写到这里，UI 只从这里读
 */
object SessionState {
    // 设备/会话
    var deviceId: String = "unknown"
    var sessionId: String = ""
    var isConnected: Boolean = false
    var isRunning: Boolean = false

    // 计数器
    var heartbeatSent: Long = 0
    var heartbeatReceived: Long = 0
    var totalScreenshots: Long = 0
    var totalUploads: Long = 0
    var totalActions: Long = 0
    var totalGestures: Long = 0
    var gestureSuccess: Long = 0
    var gestureFail: Long = 0

    // 时间戳
    var lastHeartbeatTime: Long = 0
    var lastHeartbeatAckTime: Long = 0
    var lastUploadTime: Long = 0
    var lastActionTime: Long = 0
    var lastActionType: String = ""
    var lastGestureTime: Long = 0

    // 延迟
    var pingMs: Long = 0
    var lastUploadSize: Long = 0

    // 弹幕/思维
    var currentThought: String = ""
    var currentBattleState: String = ""
    var lastError: String = ""
    private val _thoughts = mutableListOf<String>()
    fun thoughts(): List<String> = _thoughts.toList()

    fun addThought(msg: String) {
        val line = "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} 💭 $msg"
        synchronized(_thoughts) {
            _thoughts.add(line)
            if (_thoughts.size > 20) _thoughts.removeAt(0)
        }
        currentThought = msg
    }

    // ─── HUD 双轨状态 ───

    // HUD 面板 1: 👀 看见
    var hudEnemyName: String = "?"
    var hudEnemyHp: Int = 0
    var hudMyName: String = "?"
    var hudMyHp: Int = 0
    var hudWeather: String = ""

    // HUD 面板 2: 🧠 思考
    var hudThinkStage: String = "等待中..."
    var hudThinkReason: String = ""
    var hudThinkConfidence: Float = 0f
    var hudDecisionAction: String = ""
    var hudDecisionTarget: String = ""
    var hudDecisionReason: String = ""

    // HUD 面板 3: 👉 动作
    var hudExecuteType: String = ""
    var hudExecuteX: Int = 0
    var hudExecuteY: Int = 0
    var hudExecuteDelay: Long = 800L
    var hudActionCountdown: Int = 0   // 3, 2, 1, 0
    var hudActionStage: String = "等待"  // 等待 / 即将执行 / 已执行

    fun updateDecision(think: org.json.JSONObject?, decision: org.json.JSONObject?, execute: org.json.JSONObject?) {
        if (think != null) {
            hudThinkStage = think.optString("stage", "分析中")
            hudThinkReason = think.optString("reason", "")
            hudThinkConfidence = think.optDouble("confidence", 0.0).toFloat()
        }
        if (decision != null) {
            hudDecisionAction = decision.optString("action", "")
            hudDecisionTarget = decision.optString("target", "")
            hudDecisionReason = decision.optString("reason", "")
        }
        if (execute != null) {
            hudExecuteType = execute.optString("type", "tap")
            val coord = execute.optJSONArray("coordinate")
            if (coord != null && coord.length() >= 2) {
                hudExecuteX = coord.getInt(0)
                hudExecuteY = coord.getInt(1)
            }
            hudExecuteDelay = execute.optLong("delay_ms", 800L)
            hudActionCountdown = 3
            hudActionStage = "即将执行"
        }
    }

    // 实时日志（最多 50 条）
    private val _logs = mutableListOf<String>()
    fun logs(): List<String> = _logs.toList()

    fun addLog(msg: String) {
        val line = "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} $msg"
        synchronized(_logs) {
            _logs.add(line)
            if (_logs.size > 50) _logs.removeAt(0)
        }
    }

    fun reset() {
        sessionId = ""
        isConnected = false
        isRunning = false
        // 计数器不清零
        // 错误不清零
        // 日志不清零
    }
}