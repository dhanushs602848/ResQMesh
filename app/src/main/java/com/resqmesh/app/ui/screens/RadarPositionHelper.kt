package com.resqmesh.app.ui.screens

import kotlin.math.cos
import kotlin.math.sin

/**
 * Deterministic geometric helper for radar visualization.
 * Maps BLE RSSI (-40 dBm to -95 dBm) to radial distance and peer identifiers to stable bearings.
 */
object RadarPositionHelper {
    const val MAX_RSSI = -40
    const val MIN_RSSI = -95

    /**
     * Converts an RSSI value to a normalized distance between 0.0 (closest) and 1.0 (edge).
     */
    fun calculateNormalizedDistance(rssi: Int): Float {
        val clamped = rssi.coerceIn(MIN_RSSI, MAX_RSSI)
        return (MAX_RSSI - clamped).toFloat() / (MAX_RSSI - MIN_RSSI).toFloat()
    }

    /**
     * Maps RSSI to a radial distance in pixels, reserving the center zone (22% radius)
     * for the local host and bounding the outer range to 88% radius.
     */
    fun calculateRadialDistance(rssi: Int, maxRadiusPx: Float): Float {
        val norm = calculateNormalizedDistance(rssi)
        return maxRadiusPx * (0.22f + (norm * 0.66f))
    }

    /**
     * Calculates a stable, deterministic angular bearing in degrees [0, 360) from a peer identifier.
     */
    fun calculateStableBearing(identifier: String): Float {
        val hash = identifier.hashCode() and 0x7FFFFFFF
        return (hash % 360).toFloat()
    }

    /**
     * Calculates 2D Cartesian coordinates (X, Y) relative to radar center.
     */
    fun calculateBlipOffset(
        identifier: String,
        rssi: Int,
        centerX: Float,
        centerY: Float,
        maxRadiusPx: Float
    ): Pair<Float, Float> {
        val radius = calculateRadialDistance(rssi, maxRadiusPx)
        val angleDeg = calculateStableBearing(identifier)
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val x = (centerX + radius * cos(angleRad)).toFloat()
        val y = (centerY + radius * sin(angleRad)).toFloat()
        return Pair(x, y)
    }
}
