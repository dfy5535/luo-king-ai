package com.luoking.agent.models

object SessionState {
    var deviceId: String = "unknown"
    var sessionId: String = ""
    var isConnected: Boolean = false
    var isRunning: Boolean = false
    var totalScreenshots: Long = 0
    var totalActions: Long = 0
    var lastError: String = ""
    var lastActionTime: Long = 0
    var lastHeartbeatTime: Long = 0
    var lastUploadTime: Long = 0

    fun reset() {
        sessionId = ""
        isConnected = false
        isRunning = false
        lastError = ""
    }
}