package com.luoking.agent.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 输入服务 — 唯一职责：执行手势
 * 不截图、不联网、不决策
 */
class InputService : AccessibilityService() {
    companion object {
        private const val TAG = "InputService"
        private var instance: InputService? = null
        fun isRunning(): Boolean = instance != null

        fun tap(x: Int, y: Int): Boolean = instance?.performTap(x, y) ?: false
        fun swipe(x1: Int, y1: Int, x2: Int, y2: Int): Boolean = instance?.performSwipe(x1, y1, x2, y2) ?: false
        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        fun home(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
    }

    override fun onServiceConnected() {
        instance = this
        Log.d(TAG, "输入服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    private fun performTap(x: Int, y: Int): Boolean = try {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
        true
    } catch (e: Exception) { Log.e(TAG, "点击失败", e); false }

    private fun performSwipe(x1: Int, y1: Int, x2: Int, y2: Int): Boolean = try {
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 200)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
        true
    } catch (e: Exception) { Log.e(TAG, "滑动失败", e); false }
}