package com.resqmesh.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class RadarPositionTest {

    @Test
    fun `calculateNormalizedDistance maps -40 dBm to 0 and -95 dBm to 1`() {
        val closest = RadarPositionHelper.calculateNormalizedDistance(-40)
        assertEquals(0.0f, closest, 0.001f)

        val farthest = RadarPositionHelper.calculateNormalizedDistance(-95)
        assertEquals(1.0f, farthest, 0.001f)

        val mid = RadarPositionHelper.calculateNormalizedDistance(-67) // ~ midpoint between -40 and -95 (diff 55)
        assertTrue(mid in 0.45f..0.55f)
    }

    @Test
    fun `calculateNormalizedDistance clamps extreme out-of-range RSSI values`() {
        // Values stronger than -40 dBm clamp to 0.0f
        assertEquals(0.0f, RadarPositionHelper.calculateNormalizedDistance(-20), 0.001f)
        assertEquals(0.0f, RadarPositionHelper.calculateNormalizedDistance(-30), 0.001f)

        // Values weaker than -95 dBm clamp to 1.0f
        assertEquals(1.0f, RadarPositionHelper.calculateNormalizedDistance(-105), 0.001f)
        assertEquals(1.0f, RadarPositionHelper.calculateNormalizedDistance(-120), 0.001f)
    }

    @Test
    fun `calculateRadialDistance stays strictly within inner and outer radius bounds`() {
        val maxRadiusPx = 100f

        val innerRadius = RadarPositionHelper.calculateRadialDistance(-40, maxRadiusPx)
        assertEquals(22f, innerRadius, 0.01f)

        val outerRadius = RadarPositionHelper.calculateRadialDistance(-95, maxRadiusPx)
        assertEquals(88f, outerRadius, 0.01f)

        // Intermediate values must be strictly between 22 and 88
        val midRadius = RadarPositionHelper.calculateRadialDistance(-70, maxRadiusPx)
        assertTrue(midRadius in 22f..88f)
    }

    @Test
    fun `calculateStableBearing is deterministic and within 0 to 360 degrees`() {
        val id1 = "RQM-A1B2C3"
        val bearing1a = RadarPositionHelper.calculateStableBearing(id1)
        val bearing1b = RadarPositionHelper.calculateStableBearing(id1)

        assertEquals("Same ID must always produce identical bearing", bearing1a, bearing1b, 0.0001f)
        assertTrue("Bearing must be >= 0 deg", bearing1a >= 0f)
        assertTrue("Bearing must be < 360 deg", bearing1a < 360f)

        val id2 = "RQM-E4A28B"
        val bearing2 = RadarPositionHelper.calculateStableBearing(id2)
        assertNotEquals("Distinct IDs should have distinct bearings", bearing1a, bearing2)
    }

    @Test
    fun `calculateBlipOffset correctly projects coordinates around center`() {
        val centerX = 150f
        val centerY = 150f
        val maxRadius = 100f
        val id = "RQM-TEST01"

        val (x, y) = RadarPositionHelper.calculateBlipOffset(
            identifier = id,
            rssi = -60,
            centerX = centerX,
            centerY = centerY,
            maxRadiusPx = maxRadius
        )

        // Distance from center must equal calculated radial distance
        val expectedRadius = RadarPositionHelper.calculateRadialDistance(-60, maxRadius)
        val actualDistance = hypot((x - centerX).toDouble(), (y - centerY).toDouble()).toFloat()

        assertEquals(expectedRadius, actualDistance, 0.1f)
    }
}
