package com.resqmesh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resqmesh.app.ui.theme.BorderGlass
import com.resqmesh.app.ui.theme.BorderSubtle
import com.resqmesh.app.ui.theme.SurfaceCard
import com.resqmesh.app.ui.theme.SurfaceGlassNavy

/**
 * Premium Glassmorphic container with translucent backdrop and gradient rim border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = BorderSubtle,
    backgroundColor: Color = SurfaceGlassNavy,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.85f),
                        backgroundColor.copy(alpha = 0.55f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor.copy(alpha = 0.8f),
                        borderColor.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(clickModifier)
            .padding(16.dp)
    ) {
        content()
    }
}

/**
 * Glowing tactical card with colored accent luminescence.
 */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF00D9FF),
    cornerRadius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.14f),
                        glowColor.copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.75f),
                        glowColor.copy(alpha = 0.12f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(clickModifier)
            .padding(16.dp)
    ) {
        content()
    }
}

/**
 * Solid elevated command card for standard data visualization.
 */
@Composable
fun ResQCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderColor: Color = BorderGlass,
    backgroundColor: Color = SurfaceCard,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(clickModifier)
            .padding(16.dp)
    ) {
        content()
    }
}
