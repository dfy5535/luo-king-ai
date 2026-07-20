package com.luoking.agent.models

object MessageTypes {
    const val HEARTBEAT = "heartbeat"
    const val HEARTBEAT_ACK = "heartbeat_ack"
    const val UPLOAD = "upload"
    const val ACTION = "action"
    const val DECISION = "decision"  // 结构化决策: think + decision + execute
    const val ACTION_RESULT = "action_result"
    const val ERROR = "error"
    const val THOUGHT = "thought"
    const val BATTLE_STATE = "battle_state"
    const val HUD = "hud"           // HUD 显示消息
}