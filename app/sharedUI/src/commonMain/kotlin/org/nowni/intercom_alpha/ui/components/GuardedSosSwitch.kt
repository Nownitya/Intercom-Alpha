package org.nowni.intercom_alpha.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.claymorphic
import org.nowni.intercom_alpha.ui.theme.neobrutalistBorder

/**
 * Tactical Mil-Spec Guarded Emergency SOS Switch.
 * Features a safety flip-cover that prevents accidental emergency activation.
 * The rider must tap/slide the physical flip cover open before pressing the crimson SOS button.
 */
@Composable
fun GuardedSosSwitch(
    isEmergencyActive: Boolean,
    onTriggerEmergency: () -> Unit,
    onCancelEmergency: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isCoverOpen by remember { mutableStateOf(false) }
    val coverAngle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isCoverOpen) {
        coverAngle.animateTo(
            targetValue = if (isCoverOpen) -75f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Box(
        modifier = modifier
            .size(width = 96.dp, height = 96.dp)
            .neobrutalistBorder(
                color = if (isEmergencyActive) IntercomTokens.SignalEmergencyCrimson else IntercomTokens.NeobrutalBorderIdle,
                thickness = if (isEmergencyActive) 3.dp else 2.dp,
                shape = RoundedCornerShape(16.dp)
            )
            .claymorphic(
                surfaceColor = if (isEmergencyActive) Color(0xFF2A0D12) else IntercomTokens.CockpitChassis,
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // 1. Inner Emergency Crimson Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isEmergencyActive) IntercomTokens.SignalEmergencyCrimson else Color(0xFF8B1E2B))
                .clickable {
                    if (isCoverOpen) {
                        if (isEmergencyActive) onCancelEmergency() else onTriggerEmergency()
                    } else {
                        // Tapping closed cover flips it open
                        isCoverOpen = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isEmergencyActive) "ACTIVE" else "SOS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = if (isEmergencyActive) "TAP CANCEL" else "PRESS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // 2. Hinged Safety Flip Guard Cover (Striped tactical hazard cover)
        if (!isCoverOpen || coverAngle.value != -75f) {
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 84.dp)
                    .rotate(coverAngle.value)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF252A32))
                    .neobrutalistBorder(
                        color = IntercomTokens.SignalAmber,
                        thickness = 2.dp,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        isCoverOpen = !isCoverOpen
                    }
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LIFT GUARD",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        color = IntercomTokens.SignalAmber
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SAFETY FLIP",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
