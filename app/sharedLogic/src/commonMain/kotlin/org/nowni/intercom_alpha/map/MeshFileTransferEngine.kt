package org.nowni.intercom_alpha.map

import kotlinx.serialization.json.*

/**
 * Pure Kotlin Multiplatform Engine for GPX / GeoJSON Route Serialization,
 * MTU-bounded Chunking, and Loss-Tolerant File Reassembly.
 */
object MeshFileTransferEngine {

    const val DEFAULT_CHUNK_SIZE_BYTES: Int = 100

    /**
     * Splits an arbitrary byte payload into MTU-compliant chunks with Adler32 integrity checks.
     */
    fun chunkData(
        bytes: ByteArray,
        fileName: String,
        fileType: FileTransferType,
        transferId: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE_BYTES
    ): List<FileTransferChunk> {
        val totalBytes = bytes.size.toLong()
        if (bytes.isEmpty()) {
            val checksum = Adler32.checksum(ByteArray(0))
            return listOf(
                FileTransferChunk(
                    transferId = transferId,
                    fileName = fileName,
                    fileType = fileType,
                    chunkIndex = 0,
                    totalChunks = 1,
                    totalBytes = 0L,
                    data = ByteArray(0),
                    adler32Checksum = checksum
                )
            )
        }

        val totalChunks = (bytes.size + chunkSize - 1) / chunkSize
        val checksum = Adler32.checksum(bytes)
        val chunks = ArrayList<FileTransferChunk>(totalChunks)

        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, bytes.size)
            val chunkBytes = bytes.copyOfRange(start, end)

            chunks.add(
                FileTransferChunk(
                    transferId = transferId,
                    fileName = fileName,
                    fileType = fileType,
                    chunkIndex = i,
                    totalChunks = totalChunks,
                    totalBytes = totalBytes,
                    data = chunkBytes,
                    adler32Checksum = checksum
                )
            )
        }

        return chunks
    }

    /**
     * In-memory loss-tolerant reassembly buffer for incoming chunked file streams.
     */
    class ReassemblyBuffer(
        val transferId: String,
        val fileName: String,
        val fileType: FileTransferType,
        val totalChunks: Int,
        val totalBytes: Long,
        val expectedChecksum: Long
    ) {
        private val chunks = HashMap<Int, ByteArray>()

        val receivedCount: Int
            get() = chunks.size

        val isComplete: Boolean
            get() = chunks.size == totalChunks

        fun addChunk(chunk: FileTransferChunk): Boolean {
            if (chunk.transferId != transferId || chunk.chunkIndex >= totalChunks) return false
            if (chunks.containsKey(chunk.chunkIndex)) return false
            chunks[chunk.chunkIndex] = chunk.data
            return true
        }

        fun getMissingChunkIndices(): List<Int> {
            val missing = ArrayList<Int>()
            for (i in 0 until totalChunks) {
                if (!chunks.containsKey(i)) {
                    missing.add(i)
                }
            }
            return missing
        }

        fun reassemble(): ByteArray? {
            if (!isComplete) return null

            val totalSize = chunks.values.sumOf { it.size }
            val output = ByteArray(totalSize)
            var offset = 0

            for (i in 0 until totalChunks) {
                val chunkData = chunks[i] ?: return null
                chunkData.copyInto(output, offset)
                offset += chunkData.size
            }

            val computedChecksum = Adler32.checksum(output)
            if (computedChecksum != expectedChecksum) {
                return null // Checksum mismatch
            }

            return output
        }
    }

    // --- GPX (XML) Exporter & Parser ---

    /**
     * Serializes a trail polyline and associated waypoints into standard GPX 1.1 XML.
     */
    fun exportToGpx(trail: TrailPolyline, waypoints: List<TrailWaypoint>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Intercom-Alpha Off-Grid Mesh\">\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(trail.name)}</name>\n")
        sb.append("  </metadata>\n")

        for (wpt in waypoints) {
            sb.append("  <wpt lat=\"${wpt.point.latitude}\" lon=\"${wpt.point.longitude}\">\n")
            sb.append("    <ele>${wpt.point.altitudeMeters}</ele>\n")
            sb.append("    <name>${escapeXml(wpt.name)}</name>\n")
            sb.append("    <desc>${escapeXml(wpt.description)}</desc>\n")
            sb.append("    <type>${wpt.type.name}</type>\n")
            sb.append("  </wpt>\n")
        }

        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(trail.name)}</name>\n")
        sb.append("    <trkseg>\n")
        for (pt in trail.points) {
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            sb.append("        <ele>${pt.altitudeMeters}</ele>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")

        return sb.toString()
    }

    /**
     * Parses standard GPX 1.1 XML using pure Kotlin text scanning with zero XML library dependencies.
     */
    fun parseGpx(gpxString: String, defaultName: String = "Imported Trail"): Pair<TrailPolyline?, List<TrailWaypoint>> {
        val waypoints = ArrayList<TrailWaypoint>()
        val trackPoints = ArrayList<GeoPoint>()
        var trailName = defaultName

        // Extract metadata / trk name
        val nameRegex = Regex("<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
        nameRegex.find(gpxString)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotEmpty()) {
                trailName = unescapeXml(name)
            }
        }

        // Extract waypoints: <wpt lat="..." lon="...">...</wpt>
        val wptRegex = Regex("<wpt\\s+lat=[\"']([^\"']+)[\"']\\s+lon=[\"']([^\"']+)[\"'][^>]*>(.*?)</wpt>", RegexOption.DOT_MATCHES_ALL)
        for (match in wptRegex.findAll(gpxString)) {
            val lat = match.groupValues[1].toDoubleOrNull() ?: continue
            val lon = match.groupValues[2].toDoubleOrNull() ?: continue
            val inner = match.groupValues[3]

            val ele = Regex("<ele>([^<]+)</ele>").find(inner)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val name = unescapeXml(Regex("<name>([^<]+)</name>").find(inner)?.groupValues?.get(1)?.trim() ?: "Waypoint")
            val desc = unescapeXml(Regex("<desc>([^<]+)</desc>").find(inner)?.groupValues?.get(1)?.trim() ?: "")
            val typeStr = Regex("<type>([^<]+)</type>").find(inner)?.groupValues?.get(1)?.trim() ?: "CUSTOM"

            val type = try {
                WaypointType.valueOf(typeStr)
            } catch (_: Exception) {
                WaypointType.CUSTOM
            }

            waypoints.add(
                TrailWaypoint(
                    id = "wpt-${waypoints.size + 1}",
                    name = name,
                    point = GeoPoint(lat, lon, ele),
                    type = type,
                    description = desc
                )
            )
        }

        // Extract track points: <trkpt lat="..." lon="...">...</trkpt>
        val trkptRegex = Regex("<trkpt\\s+lat=[\"']([^\"']+)[\"']\\s+lon=[\"']([^\"']+)[\"'][^>]*>(.*?)</trkpt>", RegexOption.DOT_MATCHES_ALL)
        for (match in trkptRegex.findAll(gpxString)) {
            val lat = match.groupValues[1].toDoubleOrNull() ?: continue
            val lon = match.groupValues[2].toDoubleOrNull() ?: continue
            val inner = match.groupValues[3]
            val ele = Regex("<ele>([^<]+)</ele>").find(inner)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

            trackPoints.add(GeoPoint(lat, lon, ele))
        }

        val polyline = if (trackPoints.isNotEmpty()) {
            var totalDist = 0.0
            var totalAscent = 0.0
            for (i in 0 until trackPoints.size - 1) {
                totalDist += TrailMapEngine.calculateDistanceMeters(trackPoints[i], trackPoints[i + 1])
                val dAlt = trackPoints[i + 1].altitudeMeters - trackPoints[i].altitudeMeters
                if (dAlt > 0) totalAscent += dAlt
            }
            TrailPolyline(
                id = "trail-${trailName.hashCode()}",
                name = trailName,
                points = trackPoints,
                difficulty = TrailDifficulty.MODERATE,
                totalDistanceMeters = totalDist,
                totalElevationGainMeters = totalAscent
            )
        } else null

        return Pair(polyline, waypoints)
    }

    // --- GeoJSON Exporter ---

    /**
     * Serializes a trail polyline and waypoints into standard RFC 7946 GeoJSON FeatureCollection.
     */
    fun exportToGeoJson(trail: TrailPolyline, waypoints: List<TrailWaypoint>): String {
        val root = buildJsonObject {
            put("type", "FeatureCollection")
            put("features", buildJsonArray {
                // LineString for trail
                add(buildJsonObject {
                    put("type", "Feature")
                    put("geometry", buildJsonObject {
                        put("type", "LineString")
                        put("coordinates", buildJsonArray {
                            for (pt in trail.points) {
                                add(buildJsonArray {
                                    add(pt.longitude)
                                    add(pt.latitude)
                                    add(pt.altitudeMeters)
                                })
                            }
                        })
                    })
                    put("properties", buildJsonObject {
                        put("name", trail.name)
                        put("difficulty", trail.difficulty.name)
                        put("distanceMeters", trail.totalDistanceMeters)
                        put("elevationGainMeters", trail.totalElevationGainMeters)
                    })
                })

                // Points for waypoints
                for (wpt in waypoints) {
                    add(buildJsonObject {
                        put("type", "Feature")
                        put("geometry", buildJsonObject {
                            put("type", "Point")
                            put("coordinates", buildJsonArray {
                                add(wpt.point.longitude)
                                add(wpt.point.latitude)
                                add(wpt.point.altitudeMeters)
                            })
                        })
                        put("properties", buildJsonObject {
                            put("id", wpt.id)
                            put("name", wpt.name)
                            put("type", wpt.type.name)
                            put("description", wpt.description)
                        })
                    })
                }
            })
        }

        return root.toString()
    }

    private fun escapeXml(str: String): String = str
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun unescapeXml(str: String): String = str
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
