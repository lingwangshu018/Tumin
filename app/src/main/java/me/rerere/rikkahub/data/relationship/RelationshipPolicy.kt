package me.rerere.rikkahub.data.relationship

import kotlin.math.roundToInt

internal object RelationshipPolicy {
    fun clampIntensity(value: Int): Int = value.coerceIn(0, 5)

    fun scalePositiveDelta(current: Int, delta: Int): Int {
        if (delta <= 0) return delta
        val multiplier = when {
            current < 30 -> 1.0
            current < 60 -> 0.8
            current < 80 -> 0.6
            current < 95 -> 0.35
            else -> 0.15
        }
        return (delta * multiplier).roundToInt().coerceAtLeast(1)
    }

    fun repeatMultiplier(recentEventTypes: List<String>, typeName: String): Double {
        val recentMatches = recentEventTypes.takeLast(5).count { it == typeName }
        return when (recentMatches) {
            0 -> 1.0
            1 -> 0.5
            2 -> 0.25
            else -> 0.1
        }
    }

    fun capPerEvent(delta: Int, intensity: Int): Int {
        val cap = when (clampIntensity(intensity)) {
            0 -> 0
            1 -> 2
            2 -> 3
            3 -> 5
            4 -> 7
            else -> 10
        }
        return delta.coerceIn(-cap, cap)
    }
}
