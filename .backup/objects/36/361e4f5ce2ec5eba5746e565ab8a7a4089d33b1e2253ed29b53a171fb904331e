package com.luoking.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AccessibilityInputService : AccessibilityService() {
    companion object {
        private const val TAG = "InputService"
        private var instance: AccessibilityInputService? = null
        fun tap(x: Int, y: Int, durationMs: Long = 50): Boolean {
            return instance?.performTap(x, y, durationMs) ?: false
        }
        fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 200): Boolean {
            return instance?.performSwipe(x1, y1, x2, y2, durationMs) ?: false
        }
        fun back(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        }
        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        instance = this
        Log.d(TAG, "无障碍输入服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun performTap(x: Int, y: Int, durationMs: Long = 50): Boolean {
        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "点击失败: ($x, $y)", e)
            false
        }
    }

    private fun performSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 200): Boolean {
        return try {
            val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "滑动失败", e)
            false
        }
    }
}