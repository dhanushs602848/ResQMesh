package com.resqmesh.app.ui.screens

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Geometric and geographic projection helper for the Tactical Rescue Map.
 * Provides offline flat-earth equirectangular coordinate displacement and screen projection.
 */
object TacticalCoordinateHelper {

    // Approximate meters per degree latitude (WGS84 spheroid average: ~111,320m)
    const val METERS_PER_DEGREE_LAT = 111_320.0

    /**
     * Calculates flat-earth displacement in meters from origin to target.
     * @return Pair(dX_meters (East/West, positive East), dY_meters (North/South, positive North))
     */
    fun calculateDisplacementMeters(
        originLat: Double,
        originLon: Double,
        targetLat: Double,
        targetLon: Double
    ): Pair<Double, Double> {
        val latDiff = targetLat - originLat
        val lonDiff = targetLon - originLon
        val avgLatRad = Math.toRadians((originLat + targetLat) / 2.0)

        val dNorth = latDiff * METERS_PER_DEGREE_LAT
        val dEast = lonDiff * METERS_PER_DEGREE_LAT * cos(avgLatRad)
        return Pair(dEast, dNorth)
    }

    /**
     * Computes line-of-sight Euclidean distance in meters between two GPS coordinates.
     */
    fun calculateDistanceMeters(
        originLat: Double,
        originLon: Double,
        targetLat: Double,
        targetLon: Double
    ): Double {
        val (dEast, dNorth) = calculateDisplacementMeters(originLat, originLon, targetLat, targetLon)
        return hypot(dEast, dNorth)
    }

    /**
     * Projects a target GPS coordinate to 2D screen coordinates relative to center (centerX, centerY).
     * @param centerPx Screen center coordinates (X, Y)
     * @param maxRadiusPx Radius in pixels representing [rangeMeters]
     * @param rangeMeters Tactical view radius in meters
     * @return Screen coordinates (screenX, screenY) where North is -Y and East is +X
     */
    fun projectToScreen(
        originLat: Double,
        originLon: Double,
        targetLat: Double,
        targetLon: Double,
        centerPx: Pair<Float, Float>,
        maxRadiusPx: Float,
        rangeMeters: Double
    ): Pair<Float, Float> {
        val (dEast, dNorth) = calculateDisplacementMeters(originLat, originLon, targetLat, targetLon)
        val safeRange = if (rangeMeters <= 0.0) 500.0 else rangeMeters
        val pxPerMeter = maxRadiusPx / safeRange

        val screenX = (centerPx.first + (dEast * pxPerMeter)).toFloat()
        val screenY = (centerPx.second - (dNorth * pxPerMeter)).toFloat()
        return Pair(screenX, screenY)
    }
}
