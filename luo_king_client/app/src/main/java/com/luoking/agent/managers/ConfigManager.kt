package com.luoking.agent.managers

import android.content.Context
import android.content.SharedPreferences

object ConfigManager {
    private lateinit var prefs: SharedPreferences

    var serverUrl: String
        get() = prefs.getString("server_url", "ws://193.112.187.72:8765/ws") ?: "ws://193.112.187.72:8765/ws"
        set(v) { prefs.edit().putString("server_url", v).apply() }

    var screenshotInterval: Long
        get() = prefs.getLong("screenshot_interval", 1500L)
        set(v) { prefs.edit().putLong("screenshot_interval", v).apply() }

    var jpegQuality: Int
        get() = prefs.getInt("jpeg_quality", 85)
        set(v) { prefs.edit().putInt("jpeg_quality", v).apply() }

    var heartbeatInterval: Long
        get() = prefs.getLong("heartbeat_interval", 5000L)
        set(v) { prefs.edit().putLong("heartbeat_interval", v).apply() }

    var reconnectDelay: Long
        get() = prefs.getLong("reconnect_delay", 3000L)
        set(v) { prefs.edit().putLong("reconnect_delay", v).apply() }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("luo_king_config", Context.MODE_PRIVATE)
    }
}