package org.nowni.intercom_alpha.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.feature.home.state.HomeUiAction
import org.nowni.intercom_alpha.feature.home.state.HomeUiState
import org.nowni.intercom_alpha.text.CannedMessageCode
import org.nowni.intercom_alpha.ui.components.*
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.LocalTacticalColors

enum class TacticalNavTab {
    VOICE,
    RADAR,
    ELEVATION,
    CONVOY
}

@Composable
fun MainCockpitScreen(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    onNavigateToRepeater: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalTacticalColors.current
    var activeTab by remember { mutableStateOf(TacticalNavTab.VOICE) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.chassisBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Avionics Status Bar
            CockpitHeader(
                groupName = uiState.groupName,
                peerCount = maxOf(uiState.peers.size, 1),
                activeChannelId = uiState.channelState.activeChannelId,
                phyMode = uiState.phySwitcherSnapshot.shortPillLabel,
                channelUtilization = uiState.meshCongestionSnapshot.channelUtilization,
                isCongested = uiState.meshCongestionSnapshot.isThrottled,
                batteryPercentage = if (uiState.accessoryState.batteryPercent >= 0) uiState.accessoryState.batteryPercent else 85,
                isCloudConnected = uiState.isCloudBridgeConnected,
                onOpenRoster = { activeTab = TacticalNavTab.CONVOY },
                onOpenDiagnostics = {}
            )

            // 2. Main Tab Body (Anti-slop: strict tab views)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    TacticalNavTab.VOICE -> VoiceCockpitTab(uiState, onAction)
                    TacticalNavTab.RADAR -> RadarTab(uiState, onAction)
                    TacticalNavTab.ELEVATION -> ElevationTab(uiState)
                    TacticalNavTab.CONVOY -> ConvoyTab(uiState, onAction)
                }
            }

            // 3. Bottom Dock Navigation Bar (Hidden in Minimal Profile)
            if (uiState.activityProfile != org.nowni.intercom_alpha.ui.theme.ActivityProfile.MINIMAL) {
                TacticalDockBar(
                    selectedTab = activeTab,
                    onSelectTab = { activeTab = it },
                    unreadCount = uiState.unreadTextCount
                )
            }
        }
    }
}

@Composable
private fun VoiceCockpitTab(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit
) {
    val colors = LocalTacticalColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Emergency SOS Actuator
        GuardedSosButton(
            isEmergencyActive = uiState.isEmergencyActive,
            onTriggerEmergency = { onAction(HomeUiAction.TriggerSos) },
            onCancelEmergency = { onAction(HomeUiAction.ClearEmergency) }
        )

        // Live Speaker Status Hero Card (ADR-0021)
        LiveSpeakerCard(
            isTransmitting = uiState.isTransmitting,
            remoteSpeakerName = if (uiState.isTransmitting) null else uiState.peers.firstOrNull()?.name,
            activeChannelId = uiState.channelState.activeChannelId,
            peerCount = maxOf(uiState.peers.size, 1),
            showDiscreteLed = uiState.showVuMeter,
            audioLevel = uiState.inputLevel
        )

        // Optional Dual VU Acoustic Visualizer (if enabled and not low-power)
        if (uiState.showVuMeter && !uiState.lowPowerMode) {
            DualVuMeter(
                localRmsLevel = uiState.inputLevel,
                remoteRmsLevel = if (uiState.isTransmitting) 0.0f else uiState.inputLevel,
                remoteSpeakerName = if (uiState.isTransmitting) null else uiState.peers.firstOrNull()?.name,
                isVoiceActive = uiState.inputLevel > 0.05f
            )
        }

        // Channel Selector Strip (16 Channels)
        ChannelSelectorStrip(
            activeChannelId = uiState.channelState.activeChannelId,
            monitoredChannelIds = uiState.channelState.monitoredChannelIds,
            onSelectChannel = { onAction(HomeUiAction.SelectChannel(it)) },
            onToggleDualWatch = { onAction(HomeUiAction.ToggleDualWatch(it)) }
        )

        // Quick Convoy Hazard Buttons (Anti-slop: 1-tap giant pills)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HazardQuickPill(
                title = "HAZARD",
                color = colors.signalWarning,
                onClick = { onAction(HomeUiAction.SendCannedHazard(CannedMessageCode.HAZARD_ROAD)) },
                modifier = Modifier.weight(1f)
            )
            HazardQuickPill(
                title = "FUEL LOW",
                color = colors.signalActive,
                onClick = { onAction(HomeUiAction.SendCannedHazard(CannedMessageCode.FUEL_LOW)) },
                modifier = Modifier.weight(1f)
            )
            HazardQuickPill(
                title = "STOP NOW",
                color = colors.signalEmergency,
                onClick = { onAction(HomeUiAction.SendCannedHazard(CannedMessageCode.STOP_IMMEDIATE)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Master Glove-Friendly PTT Actuator
        PttActuatorShelf(
            isTransmitting = uiState.isTransmitting,
            isVoxLocked = uiState.isVoxLocked,
            activeChannelId = uiState.channelState.activeChannelId,
            onStartTransmit = { onAction(HomeUiAction.StartTransmitting) },
            onStopTransmit = { onAction(HomeUiAction.StopTransmitting) },
            onToggleVoxLock = { onAction(HomeUiAction.ToggleVoxLock) },
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
}

@Composable
private fun RadarTab(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit
) {
    val colors = LocalTacticalColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PROXIMITY RADAR",
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IntercomTokens.SignalAmberMuted)
                    .clickable { onAction(HomeUiAction.CycleRadarZoom) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "60m",
                    color = colors.signalActive,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // Radar Scope Card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(IntercomTokens.ShapeCard)
                .background(colors.surfaceCard)
                .border(1.dp, colors.surfaceBorder, IntercomTokens.ShapeCard),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${uiState.radarSnapshot.targets.size} TARGETS IN RANGE",
                    color = colors.signalActive,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "HEADING: UP • 360° MESH POLAR",
                    color = colors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ElevationTab(uiState: HomeUiState) {
    val colors = LocalTacticalColors.current
    val grade = uiState.elevationProfile.currentGradePercentage
    val deltaH = uiState.elevationProfile.leadTailVerticalSeparationMeters
    val altitude = uiState.elevationProfile.localAltitudeMeters

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "TRAIL ELEVATION",
            color = colors.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevationCard(title = "GRADE", value = "${String.format("%.1f", grade)}%", color = if (grade > 8) colors.signalWarning else colors.signalSuccess, modifier = Modifier.weight(1f))
            ElevationCard(title = "LEAD/TAIL ΔH", value = "${deltaH.toInt()}m", color = colors.signalActive, modifier = Modifier.weight(1f))
            ElevationCard(title = "ALTITUDE", value = "${altitude.toInt()}m", color = colors.textPrimary, modifier = Modifier.weight(1f))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(IntercomTokens.ShapeCard)
                .background(colors.surfaceCard)
                .border(1.dp, colors.surfaceBorder, IntercomTokens.ShapeCard),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ALPINE CONTOUR PROFILE (15 KM)",
                color = colors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ElevationCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalTacticalColors.current
    Column(
        modifier = modifier
            .clip(IntercomTokens.ShapeCard)
            .background(colors.surfaceCard)
            .border(1.dp, colors.surfaceBorder, IntercomTokens.ShapeCard)
            .padding(10.dp)
    ) {
        Text(text = title, color = colors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = color, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ConvoyTab(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit
) {
    val colors = LocalTacticalColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONVOY FLEET",
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp
            )
            Text(
                text = "${uiState.peers.size} ONLINE",
                color = colors.signalSuccess,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        // Action Buttons: Share / Join
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(IntercomTokens.SignalAmberMuted)
                    .border(1.dp, colors.signalActive.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .clickable { onAction(HomeUiAction.ToggleShareDialog(true)) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SHARE INVITE",
                    color = colors.signalActive,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.surfaceBorder, RoundedCornerShape(50))
                    .clickable { onAction(HomeUiAction.ToggleJoinDialog(true)) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SCAN QR",
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // Peer Roster List
        if (uiState.peers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "NO PEERS CONNECTED",
                        color = colors.textMuted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Share invite QR code to pair riders.",
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.peers) { peer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(IntercomTokens.ShapeCard)
                            .background(colors.surfaceCard)
                            .border(1.dp, colors.surfaceBorder, IntercomTokens.ShapeCard)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = peer.name, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "${peer.rssi} dBm", color = colors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                        Text(
                            text = if (peer.isConnected) "ONLINE" else "LOST",
                            color = if (peer.isConnected) colors.signalSuccess else colors.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HazardQuickPill(
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTacticalColors.current

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceCard)
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(50))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TacticalDockBar(
    selectedTab: TacticalNavTab,
    onSelectTab: (TacticalNavTab) -> Unit,
    unreadCount: Int
) {
    val colors = LocalTacticalColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.surfaceCard)
            .border(1.dp, colors.surfaceBorder),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TacticalNavTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val color = if (isSelected) colors.signalActive else colors.textMuted

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.name,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
