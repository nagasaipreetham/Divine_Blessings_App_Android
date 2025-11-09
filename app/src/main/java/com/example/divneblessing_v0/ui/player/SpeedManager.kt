package com.example.divneblessing_v0.ui.player

// Session-persistent speed settings (reset when app process ends)
object SpeedManager {
    private val speedMap = mutableMapOf<String, Float>()
    
    // Available speed options
    val SPEED_OPTIONS = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    
    fun getSpeed(songId: String): Float {
        return speedMap[songId] ?: 1.0f
    }
    
    fun setSpeed(songId: String, speed: Float) {
        speedMap[songId] = speed.coerceIn(0.25f, 2.0f)
    }
    
    fun resetSpeed(songId: String) {
        speedMap[songId] = 1.0f
    }
    
    fun resetAll() {
        speedMap.clear()
    }
    
    fun formatSpeed(speed: Float): String {
        return String.format("%.2fx", speed)
    }
    
    fun speedToIndex(speed: Float): Int {
        return SPEED_OPTIONS.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }.coerceAtLeast(0)
    }
    
    fun indexToSpeed(index: Int): Float {
        return SPEED_OPTIONS.getOrElse(index.coerceIn(0, SPEED_OPTIONS.size - 1)) { 1.0f }
    }
}
