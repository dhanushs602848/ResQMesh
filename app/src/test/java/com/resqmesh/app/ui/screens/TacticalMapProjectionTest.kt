package com.resqmesh.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class TacticalMapProjectionTest {

    // Center coordinates for testing (e.g. 12.9716° N, 77.5946° E)
    private val originLat = 12.9716
    private val originLon = 77.5946

    @Test
    fun `calculateDisplacementMeters returns zero displacement for identical coordinates`() {
        val (dEast, dNorth) = TacticalCoordinateHelper.calculateDisplacementMeters(
            originLat, originLon, originLat, originLon
        )
        assertEquals(0.0, dEast, 0.001)
        assertEquals(0.0, dNorth, 0.001)

        val dist = TacticalCoordinateHelper.calculateDistanceMeters(
            originLat, originLon, originLat, originLon
        )
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun `calculateDisplacementMeters accurately computes North and South offsets`() {
        // Shift latitude by +0.001 degrees (~111.3 meters North)
        val targetLat = originLat + 0.001
        val (dEast, dNorth) = TacticalCoordinateHelper.calculateDisplacementMeters(
            originLat, originLon, targetLat, originLon
        )

        assertEquals(0.0, dEast, 0.001)
        assertEquals(111.32, dNorth, 0.1)

        val distance = TacticalCoordinateHelper.calculateDistanceMeters(
            originLat, originLon, targetLat, originLon
        )
        assertEquals(111.32, distance, 0.1)
    }

    @Test
    fun `calculateDisplacementMeters accurately computes East and West offsets`() {
        // Shift longitude by +0.001 degrees East (scaled by cos(lat))
        val targetLon = originLon + 0.001
        val (dEast, dNorth) = TacticalCoordinateHelper.calculateDisplacementMeters(
            originLat, originLon, originLat, targetLon
        )

        assertEquals(0.0, dNorth, 0.001)
        // At ~13° N, cos(13°) ≈ 0.97437 -> 111.32 * 0.97437 ≈ 108.46m
        assertTrue("dEast should be approximately 108.4 meters", dEast in 107.0..110.0)

        val distance = TacticalCoordinateHelper.calculateDistanceMeters(
            originLat, originLon, originLat, targetLon
        )
        assertEquals(dEast, distance, 0.001)
    }

    @Test
    fun `projectToScreen places user origin exactly at screen center`() {
        val center = Pair(200f, 200f)
        val maxRadiusPx = 150f
        val rangeMeters = 500.0

        val (screenX, screenY) = TacticalCoordinateHelper.projectToScreen(
            originLat, originLon, originLat, originLon,
            center, maxRadiusPx, rangeMeters
        )

        assertEquals(200f, screenX, 0.001f)
        assertEquals(200f, screenY, 0.001f)
    }

    @Test
    fun `projectToScreen correctly maps Cardinal directions on Canvas`() {
        val center = Pair(200f, 200f)
        val maxRadiusPx = 150f
        val rangeMeters = 500.0

        // Target due North (positive latitude shift)
        // In screen graphics, North is UP (decreasing Y coordinate)
        val northLat = originLat + (250.0 / TacticalCoordinateHelper.METERS_PER_DEGREE_LAT) // 250m North = 50% range
        val (northX, northY) = TacticalCoordinateHelper.projectToScreen(
            originLat, originLon, northLat, originLon,
            center, maxRadiusPx, rangeMeters
        )

        assertEquals(200f, northX, 0.1f) // X stays centered
        assertTrue("North target should have screen Y less than center Y (upward)", northY < 200f)
        assertEquals(200f - 75f, northY, 0.5f) // Halfway to top ring

        // Target due East (positive longitude shift)
        // In screen graphics, East is RIGHT (increasing X coordinate)
        val (_, dNorth) = TacticalCoordinateHelper.calculateDisplacementMeters(originLat, originLon, originLat, originLon)
        assertEquals(0.0, dNorth, 0.001)
    }

    @Test
    fun `projectToScreen scales with zoom levels`() {
        val center = Pair(200f, 200f)
        val maxRadiusPx = 150f
        val targetLat = originLat + 0.001 // ~111m North

        // Standard zoom (range 500m)
        val (_, y1x) = TacticalCoordinateHelper.projectToScreen(
            originLat, originLon, targetLat, originLon,
            center, maxRadiusPx, 500.0
        )

        // Zoomed in 2x (range 250m)
        val (_, y2x) = TacticalCoordinateHelper.projectToScreen(
            originLat, originLon, targetLat, originLon,
            center, maxRadiusPx, 250.0
        )

        val distFromCenter1x = 200f - y1x
        val distFromCenter2x = 200f - y2x

        // At 2x zoom (half range), screen distance from center should be approximately double
        assertEquals(distFromCenter1x * 2.0f, distFromCenter2x, 1.0f)
    }
}
