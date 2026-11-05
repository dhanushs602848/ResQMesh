package com.resqmesh.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.ui.components.Earth3DGlobe
import com.resqmesh.app.ui.theme.DeepBlueNeon
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.EmergencyRed
import com.resqmesh.app.ui.theme.TextPrimary
import com.resqmesh.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Cinematic 7-8 second 3D Introduction Sequence:
 * 1. Pitch black space.
 * 2. 3D Earth emerges smoothly with deep lighting.
 * 3. Continents rotate and glowing cyan nodes illuminate.
 * 4. Mesh connection beams link across the globe.
 * 5. Emergency SOS pulse propagates.
 * 6. ResQMesh logo animates in with subtitle "CONNECTING PEOPLE. SAVING LIVES."
 * 7. Seamless transition to Dashboard.
 */
@Composable
fun IntroScreen(
    onFinishIntro: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    val earthScale = remember { Animatable(0.2f) }
    val earthAlpha = remember { Animatable(0f) }
    val sosPulse = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Step 1: Dark screen pause (500ms)
        delay(400)
        step = 1

        // Step 2: Earth emerges (scale 0.2 -> 1.0, alpha 0 -> 1)
        earthAlpha.animateTo(1f, animationSpec = tween(1400, easing = FastOutSlowInEasing))
        earthScale.animateTo(1.0f, animationSpec = tween(1600, easing = FastOutSlowInEasing))
        step = 2

        // Step 3: Network nodes and links active
        delay(1000)
        step = 3

        // Step 4: Red SOS pulse propagates
        sosPulse.animateTo(1.2f, animationSpec = tween(1400, easing = LinearEasing))
        step = 4

        // Step 5: ResQMesh logo reveal
        logoAlpha.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
        delay(300)

        // Step 6: Tagline "CONNECTING PEOPLE. SAVING LIVES."
        taglineAlpha.animateTo(1f, animationSpec = tween(800, easing = FastOutSlowInEasing))
        step = 5

        // Pause for viewer impact
        delay(2200)

        // Auto transition into Dashboard
        onFinishIntro()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlack)
    ) {
        // Background subtle cosmic aura
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            DeepBlueNeon.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Center Content: Rotating 3D Earth
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(280.dp)
                    .scale(earthScale.value)
                    .alpha(earthAlpha.value)
            ) {
                Earth3DGlobe(
                    modifier = Modifier.fillMaxSize(),
                    showNetworkMesh = step >= 2,
                    showEmergencyPulse = step >= 3,
                    pulseProgress = sosPulse.value
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Cinematic Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "RESQMESH",
                    style = MaterialTheme.typography.headlineLarge,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp,
                    color = TextPrimary,
                    modifier = Modifier.alpha(logoAlpha.value)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "CONNECTING PEOPLE. SAVING LIVES.",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = ElectricCyan,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(taglineAlpha.value)
                )
            }
        }

        // Top-right Skip Button
        AnimatedVisibility(
            visible = step >= 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 20.dp)
        ) {
            TextButton(
                onClick = onFinishIntro,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = TextSecondary
                )
            ) {
                Text(
                    text = "SKIP INTRO",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
