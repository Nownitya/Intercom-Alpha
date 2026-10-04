package org.nowni.intercom_alpha.headset

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.currentTimeMillis

/**
 * Supported motorcycle helmet intercom and handlebar remote brands.
 */
@Serializable
enum class HelmetBrand {
    CARDO,
    SENA,
    EJEAS,
    GENERIC_BLE_REMOTE,
    UNKNOWN
}

/**
 * PTT action event emitted by a helmet accessory or handlebar remote.
 */
@Serializable
enum class RemotePttAction {
    PRESSED,
    RELEASED,
    CLICK,
    DOUBLE_CLICK,
    LONG_PRESS
}

/**
 * Remote PTT event with brand identification and monotonic timestamp.
 */
@Serializable
data class RemotePttEvent(
    val brand: HelmetBrand,
    val action: RemotePttAction,
    val timestampMs: Long = 0L,
    val buttonCode: Int = 0
)

/**
 * Recommended audio routing mode based on connected accessory capabilities.
 */
@Serializable
enum class AudioRoutePreference {
    BLUETOOTH_SCO,
    A2DP_HIGH_FIDELITY,
    BUILTIN_SPEAKER,
    WIRED_HEADSET
}

/**
 * Comprehensive real-time state of the connected helmet accessory.
 */
@Serializable
data class HelmetAccessoryState(
    val brand: HelmetBrand = HelmetBrand.UNKNOWN,
    val deviceName: String = "",
    val macAddress: String = "",
    val isConnected: Boolean = false,
    val batteryPercent: Int = -1,
    val isPttPressed: Boolean = false,
    val pttPressDurationMs: Long = 0L,
    val audioRoute: AudioRoutePreference = AudioRoutePreference.BLUETOOTH_SCO,
    val firmwareRevision: String? = null,
    val modelNumber: String? = null
)

/**
 * Standard Bluetooth SIG & vendor-specific GATT Service and Characteristic UUIDs.
 */
object HelmetGattUuids {
    // Bluetooth SIG Standard Services
    const val BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"
    const val BATTERY_LEVEL_CHAR_UUID = "00002a19-0000-1000-8000-00805f9b34fb"

    const val DEVICE_INFO_SERVICE_UUID = "0000180a-0000-1000-8000-00805f9b34fb"
    const val MANUFACTURER_NAME_CHAR_UUID = "00002a29-0000-1000-8000-00805f9b34fb"
    const val MODEL_NUMBER_CHAR_UUID = "00002a24-0000-1000-8000-00805f9b34fb"
    const val FIRMWARE_REVISION_CHAR_UUID = "00002a26-0000-1000-8000-00805f9b34fb"

    const val HID_SERVICE_UUID = "00001812-0000-1000-8000-00805f9b34fb"
    const val HID_REPORT_CHAR_UUID = "00002a4d-0000-1000-8000-00805f9b34fb"

    // Cardo Systems Vendor GATT
    const val CARDO_CONTROL_SERVICE_UUID = "0000cd01-0000-1000-8000-00805f9b34fb"
    const val CARDO_BUTTON_CHAR_UUID = "0000cd02-0000-1000-8000-00805f9b34fb"
    const val CARDO_TELEMETRY_CHAR_UUID = "0000cd03-0000-1000-8000-00805f9b34fb"

    // Sena Technologies Vendor GATT
    const val SENA_RC_SERVICE_UUID = "0000ff00-0000-1000-8000-00805f9b34fb"
    const val SENA_BUTTON_CHAR_UUID = "0000ff01-0000-1000-8000-00805f9b34fb"
    const val SENA_STATUS_CHAR_UUID = "0000ff02-0000-1000-8000-00805f9b34fb"

    // EJEAS Universal Controller (EUC) Vendor GATT
    const val EJEAS_REMOTE_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
    const val EJEAS_BUTTON_CHAR_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
}

/**
 * Pure Kotlin Multiplatform helmet intercom and handlebar remote BLE GATT accessory manager.
 *
 * Coordinates brand detection (Cardo, Sena, EJEAS), remote PTT button events,
 * battery telemetry reporting, and SCO/A2DP audio link fallback management.
 */
class HelmetAccessoryManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _accessoryState = MutableStateFlow(HelmetAccessoryState())
    val accessoryState: StateFlow<HelmetAccessoryState> = _accessoryState.asStateFlow()

    private val _remotePttEvents = MutableSharedFlow<RemotePttEvent>(extraBufferCapacity = 64)
    val remotePttEvents: SharedFlow<RemotePttEvent> = _remotePttEvents.asSharedFlow()

    private var pttPressStartMs: Long = 0L

    /**
     * Called when a Bluetooth device is discovered or connected.
     * Identifies brand from device name and address.
     */
    fun onDeviceConnected(name: String, address: String) {
        val detectedBrand = identifyBrandFromDeviceName(name)
        _accessoryState.value = _accessoryState.value.copy(
            brand = detectedBrand,
            deviceName = name,
            macAddress = address,
            isConnected = true,
            audioRoute = if (detectedBrand != HelmetBrand.UNKNOWN) AudioRoutePreference.BLUETOOTH_SCO else AudioRoutePreference.BUILTIN_SPEAKER
        )
    }

    /**
     * Called when the accessory disconnects.
     * Cleans up state and safely resets active PTT if it was held down.
     */
    fun onDeviceDisconnected(address: String) {
        if (_accessoryState.value.macAddress == address) {
            if (_accessoryState.value.isPttPressed) {
                emitPttEvent(RemotePttAction.RELEASED, 0)
            }
            _accessoryState.value = _accessoryState.value.copy(
                isConnected = false,
                isPttPressed = false,
                pttPressDurationMs = 0L,
                audioRoute = AudioRoutePreference.BUILTIN_SPEAKER
            )
        }
    }

    /**
     * Ingests a raw BLE GATT characteristic notification and parses button/telemetry frames.
     *
     * @return `true` if characteristic was recognized and handled, `false` otherwise.
     */
    fun handleGattNotification(
        characteristicUuid: String,
        data: ByteArray,
        now: Long = currentTimeMillis()
    ): Boolean {
        if (data.isEmpty()) return false
        val uuidLower = characteristicUuid.lowercase()

        when (uuidLower) {
            // Standard SIG Battery Level (0..100%)
            HelmetGattUuids.BATTERY_LEVEL_CHAR_UUID -> {
                val level = (data[0].toInt() and 0xFF).coerceIn(0, 100)
                _accessoryState.value = _accessoryState.value.copy(batteryPercent = level)
                return true
            }

            // Standard SIG Device Information: Manufacturer Name
            HelmetGattUuids.MANUFACTURER_NAME_CHAR_UUID -> {
                val manufacturer = data.decodeToString().trim()
                val brand = identifyBrandFromManufacturer(manufacturer)
                _accessoryState.value = _accessoryState.value.copy(brand = brand)
                return true
            }

            // Standard SIG Device Information: Firmware Revision
            HelmetGattUuids.FIRMWARE_REVISION_CHAR_UUID -> {
                val fw = data.decodeToString().trim()
                _accessoryState.value = _accessoryState.value.copy(firmwareRevision = fw)
                return true
            }

            // Standard SIG Device Information: Model Number
            HelmetGattUuids.MODEL_NUMBER_CHAR_UUID -> {
                val model = data.decodeToString().trim()
                _accessoryState.value = _accessoryState.value.copy(modelNumber = model)
                return true
            }

            // Cardo Systems Button Event Characteristic
            HelmetGattUuids.CARDO_BUTTON_CHAR_UUID -> {
                parseCardoButtonNotification(data, now)
                return true
            }

            // Sena Technologies Button Event Characteristic
            HelmetGattUuids.SENA_BUTTON_CHAR_UUID -> {
                parseSenaButtonNotification(data, now)
                return true
            }

            // EJEAS Handlebar Remote Button Characteristic
            HelmetGattUuids.EJEAS_BUTTON_CHAR_UUID -> {
                parseEjeasButtonNotification(data, now)
                return true
            }

            // Standard HID Report (BLE Handlebar Remotes)
            HelmetGattUuids.HID_REPORT_CHAR_UUID -> {
                parseHidReportNotification(data, now)
                return true
            }
        }

        return false
    }

    /**
     * Cardo PackTalk / Freecom button frame parser.
     */
    private fun parseCardoButtonNotification(data: ByteArray, now: Long) {
        val opcode = data[0].toInt() and 0xFF
        val action = when (opcode) {
            0x01 -> RemotePttAction.PRESSED
            0x00 -> RemotePttAction.RELEASED
            0x02 -> RemotePttAction.CLICK
            0x03 -> RemotePttAction.DOUBLE_CLICK
            0x04 -> RemotePttAction.LONG_PRESS
            else -> return
        }
        emitPttEvent(action, opcode, HelmetBrand.CARDO, now)
    }

    /**
     * Sena 50S/50R/RC4 button frame parser.
     */
    private fun parseSenaButtonNotification(data: ByteArray, now: Long) {
        val opcode = data[0].toInt() and 0xFF
        val action = when {
            opcode == 0x10 -> RemotePttAction.PRESSED
            opcode == 0x00 -> RemotePttAction.RELEASED
            opcode == 0x01 -> RemotePttAction.CLICK
            opcode == 0x02 -> RemotePttAction.DOUBLE_CLICK
            opcode == 0x04 -> RemotePttAction.CLICK
            else -> return
        }
        emitPttEvent(action, opcode, HelmetBrand.SENA, now)
    }

    /**
     * EJEAS EUC / V6 / Q7 handlebar remote button frame parser.
     */
    private fun parseEjeasButtonNotification(data: ByteArray, now: Long) {
        val opcode = data[0].toInt() and 0xFF
        val action = when (opcode) {
            0x50 -> RemotePttAction.PRESSED
            0x51 -> RemotePttAction.RELEASED
            0x52 -> RemotePttAction.CLICK
            else -> return
        }
        emitPttEvent(action, opcode, HelmetBrand.EJEAS, now)
    }

    /**
     * Standard HID Report parser for generic Bluetooth LE handlebar remotes.
     */
    private fun parseHidReportNotification(data: ByteArray, now: Long) {
        val keyState = data[0].toInt() and 0xFF
        val action = when (keyState) {
            0x01 -> RemotePttAction.PRESSED
            0x00 -> RemotePttAction.RELEASED
            else -> return
        }
        emitPttEvent(action, keyState, HelmetBrand.GENERIC_BLE_REMOTE, now)
    }

    /**
     * Emits a typed PTT event and updates active PTT state.
     */
    private fun emitPttEvent(
        action: RemotePttAction,
        code: Int,
        brand: HelmetBrand = _accessoryState.value.brand,
        now: Long = currentTimeMillis()
    ) {
        val isPressed = when (action) {
            RemotePttAction.PRESSED -> {
                pttPressStartMs = now
                true
            }
            RemotePttAction.RELEASED -> {
                false
            }
            RemotePttAction.CLICK, RemotePttAction.DOUBLE_CLICK, RemotePttAction.LONG_PRESS -> {
                false
            }
        }

        val duration = if (!isPressed && pttPressStartMs > 0L) {
            val d = (now - pttPressStartMs).coerceAtLeast(0L)
            pttPressStartMs = 0L
            d
        } else {
            0L
        }

        _accessoryState.value = _accessoryState.value.copy(
            isPttPressed = isPressed,
            pttPressDurationMs = duration
        )

        val event = RemotePttEvent(
            brand = brand,
            action = action,
            timestampMs = now,
            buttonCode = code
        )

        scope.launch {
            _remotePttEvents.emit(event)
        }
    }

    /**
     * Safety watchdog check: Releases PTT if remote lost connection or button remained
     * stuck down for more than 60 seconds (prevents runaway microphone transmissions).
     */
    fun checkRunawayWatchdog(now: Long = currentTimeMillis()): Boolean {
        if (_accessoryState.value.isPttPressed && pttPressStartMs > 0L) {
            val duration = now - pttPressStartMs
            if (duration >= RUNAWAY_PTT_THRESHOLD_MS) {
                emitPttEvent(RemotePttAction.RELEASED, 0)
                return true
            }
        }
        return false
    }

    /**
     * Manually overrides audio route preference.
     */
    fun setAudioRoutePreference(preference: AudioRoutePreference) {
        _accessoryState.value = _accessoryState.value.copy(audioRoute = preference)
    }

    companion object {
        const val RUNAWAY_PTT_THRESHOLD_MS = 60_000L // 60 seconds

        fun identifyBrandFromDeviceName(name: String): HelmetBrand {
            val lower = name.lowercase()
            return when {
                lower.contains("cardo") || lower.contains("packtalk") || lower.contains("freecom") -> HelmetBrand.CARDO
                lower.contains("sena") || lower.contains("50s") || lower.contains("50r") || lower.contains("60s") ||
                        lower.contains("30k") || lower.contains("20s") || lower.contains("rc4") -> HelmetBrand.SENA
                lower.contains("ejeas") || lower.contains("v6") || lower.contains("q7") ||
                        lower.contains("ms8") || lower.contains("euc") || lower.contains("vnetphone") -> HelmetBrand.EJEAS
                lower.contains("remote") || lower.contains("ptt") || lower.contains("handlebar") -> HelmetBrand.GENERIC_BLE_REMOTE
                else -> HelmetBrand.UNKNOWN
            }
        }

        fun identifyBrandFromManufacturer(manufacturer: String): HelmetBrand {
            val lower = manufacturer.lowercase()
            return when {
                lower.contains("cardo") -> HelmetBrand.CARDO
                lower.contains("sena") -> HelmetBrand.SENA
                lower.contains("ejeas") || lower.contains("vnetphone") -> HelmetBrand.EJEAS
                else -> HelmetBrand.GENERIC_BLE_REMOTE
            }
        }
    }
}
