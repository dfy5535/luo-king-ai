package com.luoking.agent.models

object MessageTypes {
    const val HEARTBEAT = "heartbeat"
    const val HEARTBEAT_ACK = "heartbeat_ack"
    const val UPLOAD = "upload"
    const val ACTION = "action"
    const val ACTION_RESULT = "action_result"
    const val ERROR = "error"
}

object SessionState {
    var deviceId: String = "unknown"
    var serverUrl: String = "ws://193.112.187.72:8765"
    var sessionId: String = ""
    var phase: String = "unknown"
    var isConnected: Boolean = false
    var isRunning: Boolean = false
    var screenWidth: Int = 1080
    var screenHeight: Int = 1920
    var totalScreenshots: Long = 0
    var totalActions: Long = 0
    var consecutiveFailures: Int = 0
    var screenshotIntervalMs: Long = 1500L
    var lastError: String = ""
}