package com.luoking.agent.managers

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import com.luoking.agent.services.InputService

object PermissionManager {
    var accessibilityGranted: Boolean = false
    var projectionGranted: Boolean = false
    var notificationGranted: Boolean = false

    fun isAccessibilityEnabled(context: Context): Boolean {
        val service = "${context.packageName}/${InputService::class.java.canonicalName}"
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(":")?.any { it.equals(service, ignoreCase = true) } ?: false
        } catch (_: Exception) { false }
    }

    fun requestAccessibility(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun requestScreenCapture(context: Context): Intent {
        val pm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return pm.createScreenCaptureIntent()
    }

    fun checkAll(context: Context): List<String> {
        val missing = mutableListOf<String>()
        if (!isAccessibilityEnabled(context)) missing.add("无障碍")
        if (!projectionGranted) missing.add("截屏")
        return missing
    }
}