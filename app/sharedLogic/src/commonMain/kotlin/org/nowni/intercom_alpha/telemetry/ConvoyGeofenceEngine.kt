package org.nowni.intercom_alpha.telemetry

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.map.TrailWaypoint
import org.nowni.intercom_alpha.map.WaypointType

/**
 * Tactical engine for real-time convoy geofencing, obstacle waypoint proximity alerts,
 * and convoy bifurcation / cluster split detection.
 *
 * Runs 100% in pure Kotlin Multiplatform with zero Java or platform dependencies (Rules 2 & 3).
 */
class ConvoyGeofenceEngine(
    val warningThresholdMeters: Double = 300.0,
    val breachThresholdMeters: Double = 600.0,
    val hazardProximityRadiusMeters: Double = 150.0,
    val splitClusterMinDistanceMeters: Double = 500.0
) {
    private val mutex = Mutex()

    private val _geofenceState = MutableStateFlow(ConvoyGeofenceSnapshot())
    val geofenceState: StateFlow<ConvoyGeofenceSnapshot> = _geofenceState.asStateFlow()

    private val _outgoingAlarms = Channel<GeofenceAlarmPacket>(Channel.BUFFERED)
    val outgoingAlarms: ReceiveChannel<GeofenceAlarmPacket> = _outgoingAlarms

    // Cache of previous distances to waypoints for approaching/receding trend calculation
    private val previousWaypointDistances = mutableMapOf<String, Double>()

    // Set of currently active breach peer IDs to avoid spamming duplicate alarm packets
    private val activeBreachPeerIds = mutableSetOf<String>()
    private val activeHazardWaypointIds = mutableSetOf<String>()

    private var currentLocalCoord: GpsCoordinate? = null
    private val currentRemoteRiders = mutableMapOf<String, ConvoyRiderPosition>()
    private var activeWaypoints: List<TrailWaypoint> = emptyList()
    var leadRiderPeerId: String? = null
        private set

    fun setLeadRider(leadPeerId: String?) {
        leadRiderPeerId = leadPeerId
    }

    fun setActiveWaypoints(waypoints: List<TrailWaypoint>) {
        activeWaypoints = waypoints
    }

    suspend fun updateLocalPosition(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double = 0.0,
        speedKmh: Float = 0f,
        headingDegrees: Float = 0f,
        timestampMs: Long = currentTimeMillis()
    ): ConvoyGeofenceSnapshot = updateLocalPosition(
        GpsCoordinate(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            speedKmh = speedKmh,
            headingDegrees = headingDegrees,
            timestampMs = timestampMs
        )
    )

    suspend fun updateLocalPosition(coord: GpsCoordinate): ConvoyGeofenceSnapshot {
        val waypoints: List<TrailWaypoint>
        val riders: List<ConvoyRiderPosition>
        mutex.withLock {
            currentLocalCoord = coord
            waypoints = activeWaypoints
            riders = currentRemoteRiders.values.toList()
        }
        return evaluate(coord, riders, waypoints, coord.timestampMs)
    }

    suspend fun handleRemoteTelemetry(frame: ConvoyTelemetryFrame) {
        val coord: GpsCoordinate?
        val waypoints: List<TrailWaypoint>
        val riders: List<ConvoyRiderPosition>
        mutex.withLock {
            coord = currentLocalCoord
            val dist = coord?.let {
                ConvoyGpsTracker.calculateHaversineDistance(it.latitude, it.longitude, frame.coordinate.latitude, frame.coordinate.longitude)
            } ?: 0.0
            val absBearing = coord?.let {
                ConvoyGpsTracker.calculateForwardAzimuth(it.latitude, it.longitude, frame.coordinate.latitude, frame.coordinate.longitude)
            } ?: 0.0
            val relBearing = coord?.let {
                ConvoyGpsTracker.calculateRelativeBearing(absBearing, it.headingDegrees)
            } ?: 0.0
            val isAhead = relBearing >= -90.0 && relBearing <= 90.0

            val rider = ConvoyRiderPosition(
                peerId = frame.peerId,
                peerName = frame.peerName,
                coordinate = frame.coordinate,
                distanceMeters = dist,
                absoluteBearingDegrees = absBearing,
                relativeBearingDegrees = relBearing,
                isAhead = isAhead,
                isBehind = !isAhead,
                motionState = frame.motionState,
                isEmergencyAlert = frame.isEmergencyAlert,
                lastUpdatedMs = frame.coordinate.timestampMs
            )
            currentRemoteRiders[frame.peerId] = rider
            waypoints = activeWaypoints
            riders = currentRemoteRiders.values.toList()
        }
        evaluate(coord, riders, waypoints, frame.coordinate.timestampMs)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun serializeAlarmPacket(packet: GeofenceAlarmPacket): ByteArray =
            json.encodeToString(GeofenceAlarmPacket.serializer(), packet).encodeToByteArray()

        fun deserializeAlarmPacket(bytes: ByteArray): GeofenceAlarmPacket =
            json.decodeFromString(GeofenceAlarmPacket.serializer(), bytes.decodeToString())
    }

    /**
     * Updates and re-evaluates the entire geofence state given current local telemetry,
     * remote convoy riders, and loaded trail waypoints.
     */
    suspend fun evaluate(
        localCoord: GpsCoordinate?,
        remoteRiders: List<ConvoyRiderPosition>,
        waypoints: List<TrailWaypoint> = emptyList(),
        now: Long = currentTimeMillis()
    ): ConvoyGeofenceSnapshot = mutex.withLock {
        if (localCoord == null) {
            val emptySnapshot = ConvoyGeofenceSnapshot(
                overallStatus = GeofenceTetherStatus.IN_BOUNDS,
                timestampMs = now
            )
            _geofenceState.value = emptySnapshot
            return@withLock emptySnapshot
        }

        // 1. Evaluate Tether Bounds for Remote Riders
        val tetherAlerts = mutableListOf<RiderTetherAlert>()
        var furthestDist = 0.0
        var worstStatus = GeofenceTetherStatus.IN_BOUNDS

        for (rider in remoteRiders) {
            if (rider.isStale) {
                tetherAlerts.add(
                    RiderTetherAlert(
                        peerId = rider.peerId,
                        peerName = rider.peerName,
                        distanceMeters = rider.distanceMeters,
                        status = GeofenceTetherStatus.DISCONNECTED,
                        isTrailingBehind = rider.isBehind,
                        timestampMs = now
                    )
                )
                continue
            }

            val dist = rider.distanceMeters
            if (dist > furthestDist) furthestDist = dist

            val status = when {
                dist > breachThresholdMeters -> GeofenceTetherStatus.BREACH
                dist > warningThresholdMeters -> GeofenceTetherStatus.WARNING
                else -> GeofenceTetherStatus.IN_BOUNDS
            }

            if (status == GeofenceTetherStatus.BREACH) {
                worstStatus = GeofenceTetherStatus.BREACH
                if (!activeBreachPeerIds.contains(rider.peerId)) {
                    activeBreachPeerIds.add(rider.peerId)
                    _outgoingAlarms.trySend(
                        GeofenceAlarmPacket(
                            senderId = "local-rider",
                            alarmType = GeofenceAlarmType.TETHER_BREACH,
                            targetId = rider.peerId,
                            title = "CONVOY TETHER BREACH",
                            details = "${rider.peerName} separated by ${dist.toInt()}m (> ${breachThresholdMeters.toInt()}m)",
                            distanceMeters = dist,
                            timestampMs = now
                        )
                    )
                }
            } else {
                activeBreachPeerIds.remove(rider.peerId)
                if (status == GeofenceTetherStatus.WARNING && worstStatus != GeofenceTetherStatus.BREACH) {
                    worstStatus = GeofenceTetherStatus.WARNING
                }
            }

            tetherAlerts.add(
                RiderTetherAlert(
                    peerId = rider.peerId,
                    peerName = rider.peerName,
                    distanceMeters = dist,
                    status = status,
                    isTrailingBehind = rider.isBehind,
                    timestampMs = now
                )
            )
        }

        // 2. Evaluate Proximity to Hazard Waypoints
        val hazardAlerts = mutableListOf<HazardProximityAlert>()
        for (wp in waypoints) {
            val dist = ConvoyGpsTracker.calculateHaversineDistance(
                localCoord.latitude, localCoord.longitude,
                wp.point.latitude, wp.point.longitude
            )

            if (dist <= hazardProximityRadiusMeters) {
                val prevDist = previousWaypointDistances[wp.id] ?: (dist + 1.0)
                val isApproaching = dist <= prevDist
                previousWaypointDistances[wp.id] = dist

                val forwardAzimuth = ConvoyGpsTracker.calculateForwardAzimuth(
                    localCoord.latitude, localCoord.longitude,
                    wp.point.latitude, wp.point.longitude
                )
                val relBearing = ConvoyGpsTracker.calculateRelativeBearing(forwardAzimuth, localCoord.headingDegrees)

                val alert = HazardProximityAlert(
                    waypointId = wp.id,
                    waypointName = wp.name,
                    waypointType = wp.type,
                    distanceMeters = dist,
                    relativeBearingDegrees = relBearing,
                    elevationMeters = wp.point.altitudeMeters,
                    isApproaching = isApproaching,
                    timestampMs = now
                )
                hazardAlerts.add(alert)

                // Emit over-the-air alarm on first detection of hazardous waypoint proximity
                val isHazardousType = wp.type == WaypointType.OBSTACLE_HAZARD ||
                        wp.type == WaypointType.MOUNTAIN_PASS
                if (isHazardousType && !activeHazardWaypointIds.contains(wp.id)) {
                    activeHazardWaypointIds.add(wp.id)
                    _outgoingAlarms.trySend(
                        GeofenceAlarmPacket(
                            senderId = "local-rider",
                            alarmType = GeofenceAlarmType.HAZARD_PROXIMITY,
                            targetId = wp.id,
                            title = "HAZARD AHEAD: ${wp.name.uppercase()}",
                            details = "Approaching hazard waypoint (${dist.toInt()}m ahead)",
                            distanceMeters = dist,
                            timestampMs = now
                        )
                    )
                }
            } else {
                previousWaypointDistances.remove(wp.id)
                activeHazardWaypointIds.remove(wp.id)
            }
        }

        // 3. Evaluate Convoy Cluster Partition / Split Detection
        val splitWarning = detectConvoySplitInternal(localCoord, remoteRiders, now)
        if (splitWarning?.isSplitDetected == true) {
            _outgoingAlarms.trySend(
                GeofenceAlarmPacket(
                    senderId = "local-rider",
                    alarmType = GeofenceAlarmType.CONVOY_SPLIT,
                    title = "CONVOY SPLIT DETECTED",
                    details = splitWarning.recommendedAction,
                    distanceMeters = splitWarning.clusterGapMeters,
                    timestampMs = now
                )
            )
        }

        val snapshot = ConvoyGeofenceSnapshot(
            overallStatus = worstStatus,
            furthestRiderDistanceMeters = furthestDist,
            tetherAlerts = tetherAlerts.sortedByDescending { it.distanceMeters },
            activeHazardAlerts = hazardAlerts.sortedBy { it.distanceMeters },
            splitWarning = splitWarning,
            timestampMs = now
        )

        _geofenceState.value = snapshot
        return@withLock snapshot
    }

    /**
     * Analyzes inter-rider distances to detect if the convoy has fractured into disconnected clusters.
     */
    private fun detectConvoySplitInternal(
        localCoord: GpsCoordinate,
        remoteRiders: List<ConvoyRiderPosition>,
        now: Long
    ): ConvoySplitWarning? {
        val activeRiders = remoteRiders.filter { !it.isStale }
        if (activeRiders.size < 2) return null

        // Gather all rider locations including local rider
        val allPoints = mutableListOf<GpsCoordinate>()
        allPoints.add(localCoord)
        activeRiders.forEach { allPoints.add(it.coordinate) }

        // Find connected components using single-linkage threshold
        val visited = BooleanArray(allPoints.size)
        val components = mutableListOf<List<Int>>()

        for (i in allPoints.indices) {
            if (!visited[i]) {
                val comp = mutableListOf<Int>()
                val queue = ArrayDeque<Int>()
                queue.add(i)
                visited[i] = true

                while (queue.isNotEmpty()) {
                    val curr = queue.removeFirst()
                    comp.add(curr)

                    for (next in allPoints.indices) {
                        if (!visited[next]) {
                            val dist = ConvoyGpsTracker.calculateHaversineDistance(
                                allPoints[curr].latitude, allPoints[curr].longitude,
                                allPoints[next].latitude, allPoints[next].longitude
                            )
                            if (dist < splitClusterMinDistanceMeters) {
                                visited[next] = true
                                queue.add(next)
                            }
                        }
                    }
                }
                components.add(comp)
            }
        }

        val isSplit = components.size > 1

        if (isSplit) {
            val localIndices = components.first { it.contains(0) }
            val remoteIndices = allPoints.indices.filter { !localIndices.contains(it) }

            var minClusterGap = Double.MAX_VALUE
            for (l in localIndices) {
                for (r in remoteIndices) {
                    val d = ConvoyGpsTracker.calculateHaversineDistance(
                        allPoints[l].latitude, allPoints[l].longitude,
                        allPoints[r].latitude, allPoints[r].longitude
                    )
                    if (d < minClusterGap) minClusterGap = d
                }
            }

            val gapInt = minClusterGap.toInt()
            val action = "Convoy split detected (Gap: ${gapInt}m). Sweep rider or stationary repeater recommended to bridge sub-clusters."

            return ConvoySplitWarning(
                isSplitDetected = true,
                clusterCount = components.size,
                clusterGapMeters = minClusterGap,
                localClusterSize = localIndices.size,
                remoteClusterSize = remoteIndices.size,
                recommendedAction = action,
                timestampMs = now
            )
        }

        // Single component: calculate maximum adjacent step gap
        var maxAdjacentGap = 0.0
        for (i in allPoints.indices) {
            for (j in i + 1 until allPoints.size) {
                val d = ConvoyGpsTracker.calculateHaversineDistance(
                    allPoints[i].latitude, allPoints[i].longitude,
                    allPoints[j].latitude, allPoints[j].longitude
                )
                if (d < splitClusterMinDistanceMeters && d > maxAdjacentGap) {
                    maxAdjacentGap = d
                }
            }
        }

        return ConvoySplitWarning(
            isSplitDetected = false,
            clusterCount = 1,
            clusterGapMeters = maxAdjacentGap,
            localClusterSize = allPoints.size,
            remoteClusterSize = 0,
            recommendedAction = "Convoy unified in single cluster.",
            timestampMs = now
        )
    }

    /**
     * Ingest an over-the-air alarm packet received from a peer node.
     */
    suspend fun handleIncomingAlarm(packet: GeofenceAlarmPacket) = mutex.withLock {
        // Can be logged or updated into state if needed
    }

    /**
     * Clear all cached states and active alert records.
     */
    suspend fun reset() = mutex.withLock {
        previousWaypointDistances.clear()
        activeBreachPeerIds.clear()
        activeHazardWaypointIds.clear()
        currentLocalCoord = null
        currentRemoteRiders.clear()
        activeWaypoints = emptyList()
        leadRiderPeerId = null
        _geofenceState.value = ConvoyGeofenceSnapshot()
    }
}
