package com.luoking.agent.models

/**
 * 坐标管理器 — 服务器只发语义动作，APK 翻译成真实坐标
 * 换手机只改这里，服务器不动
 */
object CoordinateManager {
    private const val BASE_W = 1080
    private const val BASE_H = 1920

    // 技能按钮坐标（基准 1080x1920）
    private val SKILLS = mapOf(1 to Pair(250, 1000), 2 to Pair(650, 1000), 3 to Pair(250, 1100), 4 to Pair(650, 1100))
    private val SWAPS = mapOf(1 to Pair(1000, 1000), 2 to Pair(1300, 1000), 3 to Pair(1600, 1000))

    var screenWidth: Int = 1080
    var screenHeight: Int = 1920

    fun skill(index: Int): Pair<Int, Int>? = SKILLS[index]?.let { scale(it) }
    fun swap(index: Int): Pair<Int, Int>? = SWAPS[index]?.let { scale(it) }
    fun center(): Pair<Int, Int> = Pair(screenWidth / 2, screenHeight / 2)
    fun tap(x: Int, y: Int): Pair<Int, Int> = Pair(x, y)

    private fun scale(c: Pair<Int, Int>): Pair<Int, Int> {
        val sx = screenWidth.toFloat() / BASE_W
        val sy = screenHeight.toFloat() / BASE_H
        return Pair((c.first * sx).toInt(), (c.second * sy).toInt())
    }
}