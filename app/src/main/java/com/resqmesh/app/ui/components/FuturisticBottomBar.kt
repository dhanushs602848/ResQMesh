package com.resqmesh.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmergencyShare
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.navigation.Screen
import com.resqmesh.app.ui.theme.BorderGlass
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.SurfaceCard
import com.resqmesh.app.ui.theme.SurfaceGlassNavy
import com.resqmesh.app.ui.theme.TextSecondary

sealed class NavItem(val screen: Screen, val label: String, val icon: ImageVector) {
    data object Home : NavItem(Screen.Dashboard, "Home", Icons.Default.Public)
    data object Network : NavItem(Screen.MeshNetwork, "Network", Icons.Default.Hub)
    data object Alerts : NavItem(Screen.Alerts, "Alerts", Icons.Default.Notifications)
    data object Map : NavItem(Screen.RescueMap, "Map", Icons.Default.Map)
    data object Settings : NavItem(Screen.Settings, "Config", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    NavItem.Home,
    NavItem.Network,
    NavItem.Alerts,
    NavItem.Map,
    NavItem.Settings
)

/**
 * Premium floating glassmorphic bottom navigation bar with glow indicator.
 */
@Composable
fun FuturisticBottomBar(
    currentRoute: String,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SurfaceGlassNavy.copy(alpha = 0.95f),
                        DeepSpaceBlack.copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BorderGlass.copy(alpha = 0.6f),
                        BorderGlass.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.screen.route
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) ElectricCyan else TextSecondary,
                    animationSpec = tween(250),
                    label = "nav_icon_color"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isSelected) {
                                onNavigate(item.screen)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                            )
                        }
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = iconColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
