package com.resqmesh.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.resqmesh.app.ui.theme.DeepBlueNeon
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.EmergencyRed
import com.resqmesh.app.ui.theme.SafeGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class EarthNode(
    val lat: Float,
    val lon: Float,
    val color: Color = ElectricCyan,
    val isEmergency: Boolean = false
)

/**
 * Procedural 3D Earth Globe Canvas rendering with rotating wireframe meridians,
 * glowing atmospheric aura, continent silhouettes, and dynamic mesh communication links.
 * Supports interactive drag-to-rotate touch gestures.
 */
@Composable
fun Earth3DGlobe(
    modifier: Modifier = Modifier,
    rotationSpeedMs: Int = 24000,
    showNetworkMesh: Boolean = true,
    showEmergencyPulse: Boolean = true,
    pulseProgress: Float = 0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "earth_globe")

    // Auto-rotation angle 0..2*PI
    val autoRotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(rotationSpeedMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "globe_rotation"
    )

    // Touch gesture drag offset for manual interactive rotation
    var manualDragOffset by remember { mutableFloatStateOf(0f) }

    val effectiveRotation = autoRotationAngle + manualDragOffset

    // Secondary pulse for nodes
    val nodeGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "node_glow"
    )

    // Predefined geographic communications nodes (lat/lon in radians)
    val meshNodes = remember {
        listOf(
            EarthNode(0.35f, 0.4f, ElectricCyan),
            EarthNode(0.55f, 1.8f, SafeGreen),
            EarthNode(-0.25f, 2.3f, ElectricCyan),
            EarthNode(0.70f, 3.2f, ElectricCyan),
            EarthNode(-0.45f, 4.0f, SafeGreen),
            EarthNode(0.20f, 4.8f, EmergencyRed, isEmergency = true),
            EarthNode(-0.15f, 5.5f, ElectricCyan),
            EarthNode(0.40f, 0.9f, SafeGreen),
            EarthNode(-0.60f, 1.4f, ElectricCyan)
        )
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, _, _ ->
                manualDragOffset += pan.x * 0.008f
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2.15f
            val center = Offset(size.width / 2f, size.height / 2f)

            // 1. Atmospheric Glow Behind Globe
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DeepBlueNeon.copy(alpha = 0.35f),
                        ElectricCyan.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.35f
                ),
                radius = radius * 1.35f,
                center = center
            )

            // 2. Earth Sphere Deep Dark Surface
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0C1935), // Illuminated hemisphere
                        Color(0xFF070F22),
                        DeepSpaceBlack
                    ),
                    center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                    radius = radius * 1.15f
                ),
                radius = radius,
                center = center
            )

            // 3. 3D Latitudinal Parallels
            val latitudes = listOf(-0.6f, -0.3f, 0f, 0.3f, 0.6f)
            for (lat in latitudes) {
                val yOffset = radius * sin(lat)
                val latRadius = radius * cos(lat)
                drawOval(
                    color = DeepBlueNeon.copy(alpha = 0.22f),
                    topLeft = Offset(center.x - latRadius, center.y + yOffset - (latRadius * 0.15f)),
                    size = androidx.compose.ui.geometry.Size(latRadius * 2, latRadius * 0.30f),
                    style = Stroke(width = 1.2f)
                )
            }

            // 4. 3D Rotating Longitudinal Meridians
            val meridianCount = 12
            for (i in 0 until meridianCount) {
                val baseLon = (i * (2 * PI / meridianCount)).toFloat()
                val currentLon = baseLon + effectiveRotation
                val cosLon = cos(currentLon)
                val isVisible = cosLon >= -0.2f // semi-hemisphere visible

                if (isVisible) {
                    val meridianWidth = radius * cosLon
                    val alpha = (cosLon + 0.2f).coerceIn(0.08f, 0.45f)
                    drawOval(
                        color = ElectricCyan.copy(alpha = alpha),
                        topLeft = Offset(center.x - meridianWidth, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(meridianWidth * 2, radius * 2),
                        style = Stroke(width = 1.2f)
                    )
                }
            }

            // 5. Projected 3D Landmass Point Cloud (Simulated continents)
            drawProceduralContinents(center, radius, effectiveRotation)

            // 6. Network Nodes & Communication Beams
            if (showNetworkMesh) {
                val projectedPoints = mutableListOf<Triple<Offset, EarthNode, Boolean>>()

                meshNodes.forEach { node ->
                    val effectiveLon = node.lon + effectiveRotation
                    val cosLon = cos(effectiveLon)
                    val sinLon = sin(effectiveLon)
                    val cosLat = cos(node.lat)
                    val sinLat = sin(node.lat)

                    // 3D projection onto 2D screen
                    val z = cosLat * cosLon
                    if (z > 0.05f) { // visible on front hemisphere
                        val px = center.x + radius * cosLat * sinLon
                        val py = center.y - radius * sinLat
                        projectedPoints.add(Triple(Offset(px, py), node, true))
                    }
                }

                // Draw glowing communication paths between visible nodes
                for (i in projectedPoints.indices) {
                    for (j in (i + 1) until projectedPoints.size) {
                        val (p1, node1) = projectedPoints[i]
                        val (p2, node2) = projectedPoints[j]
                        val distance = (p1 - p2).getDistance()

                        if (distance < radius * 1.1f) {
                            val pathColor = if (node1.isEmergency || node2.isEmergency) {
                                EmergencyRed.copy(alpha = 0.5f)
                            } else {
                                ElectricCyan.copy(alpha = 0.35f)
                            }

                            // Curved arched communication beam
                            val midX = (p1.x + p2.x) / 2f
                            val midY = (p1.y + p2.y) / 2f - (distance * 0.15f)
                            val beamPath = Path().apply {
                                moveTo(p1.x, p1.y)
                                quadraticTo(midX, midY, p2.x, p2.y)
                            }

                            drawPath(
                                path = beamPath,
                                color = pathColor,
                                style = Stroke(
                                    width = 1.8f,
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                            )
                        }
                    }
                }

                // Draw Nodes
                projectedPoints.forEach { (pos, node) ->
                    val color = if (node.isEmergency) EmergencyRed else node.color
                    // Glow ring
                    drawCircle(
                        color = color.copy(alpha = 0.25f * nodeGlow),
                        radius = 12f * nodeGlow,
                        center = pos
                    )
                    // Core point
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f,
                        center = pos
                    )
                    drawCircle(
                        color = color,
                        radius = 5.5f,
                        center = pos,
                        style = Stroke(width = 2f)
                    )
                }
            }

            // 7. Outer Atmosphere Rim Lighting
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        ElectricCyan.copy(alpha = 0.3f),
                        ElectricCyan.copy(alpha = 0.7f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f),
                    radius = radius * 1.05f
                ),
                radius = radius,
                center = center,
                style = Stroke(width = 3.5f)
            )

            // 8. Dynamic Emergency SOS Wave Travelling across Globe
            if (showEmergencyPulse && pulseProgress > 0f) {
                val waveRadius = radius * pulseProgress
                drawCircle(
                    color = EmergencyRed.copy(alpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.65f),
                    radius = waveRadius,
                    center = center,
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}

/**
 * Draws procedurally placed land clusters that rotate correctly around the spherical geometry.
 */
private fun DrawScope.drawProceduralContinents(center: Offset, radius: Float, rotationAngle: Float) {
    val continents = listOf(
        Pair(0.7f, 3.8f), Pair(0.6f, 4.0f), Pair(0.8f, 4.2f), Pair(0.5f, 3.6f),
        Pair(0.8f, 0.2f), Pair(0.6f, 0.4f), Pair(0.1f, 0.3f), Pair(-0.3f, 0.4f), Pair(-0.5f, 0.5f),
        Pair(0.6f, 1.4f), Pair(0.5f, 1.8f), Pair(0.7f, 2.0f), Pair(0.3f, 1.7f),
        Pair(-0.2f, 4.8f), Pair(-0.5f, 5.0f), Pair(-0.7f, 5.1f),
        Pair(-0.45f, 2.3f), Pair(-0.55f, 2.4f)
    )

    continents.forEach { (lat, lon) ->
        val effLon = lon + rotationAngle
        val cosLon = cos(effLon)
        val sinLon = sin(effLon)
        val cosLat = cos(lat)
        val sinLat = sin(lat)

        val z = cosLat * cosLon
        if (z > 0.05f) {
            val px = center.x + radius * cosLat * sinLon
            val py = center.y - radius * sinLat
            val size = (14f * z).coerceIn(4f, 22f)

            drawCircle(
                color = DeepBlueNeon.copy(alpha = (0.25f * z).coerceIn(0.05f, 0.45f)),
                radius = size,
                center = Offset(px, py)
            )
        }
    }
}
