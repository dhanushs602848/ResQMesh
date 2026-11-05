package com.resqmesh.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PulsingDot(
    modifier: Modifier = Modifier,
    color: Color,
    size: Dp = 10.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing wave
        Box(
            modifier = Modifier
                .size(size * 2.4f)
                .scale(scale)
                .background(color.copy(alpha = alpha), CircleShape)
        )
        // Core emitter dot
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}
