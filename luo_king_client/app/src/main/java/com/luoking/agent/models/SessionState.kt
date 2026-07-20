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