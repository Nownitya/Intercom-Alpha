package org.nowni.intercom_alpha.headset

import kotlin.test.*

class HelmetAccessoryManagerTest {

    @Test
    fun testBrandIdentificationFromDeviceNames() {
        assertEquals(HelmetBrand.CARDO, HelmetAccessoryManager.identifyBrandFromDeviceName("Cardo PACKTALK EDGE"))
        assertEquals(HelmetBrand.CARDO, HelmetAccessoryManager.identifyBrandFromDeviceName("FREECOM 4x"))
        assertEquals(HelmetBrand.SENA, HelmetAccessoryManager.identifyBrandFromDeviceName("Sena 50S v2.2"))
        assertEquals(HelmetBrand.SENA, HelmetAccessoryManager.identifyBrandFromDeviceName("Sena RC4 Remote"))
        assertEquals(HelmetBrand.EJEAS, HelmetAccessoryManager.identifyBrandFromDeviceName("EJEAS V6 Pro"))
        assertEquals(HelmetBrand.EJEAS, HelmetAccessoryManager.identifyBrandFromDeviceName("EUC Handlebar"))
        assertEquals(HelmetBrand.GENERIC_BLE_REMOTE, HelmetAccessoryManager.identifyBrandFromDeviceName("BLE PTT Button"))
        assertEquals(HelmetBrand.UNKNOWN, HelmetAccessoryManager.identifyBrandFromDeviceName("Smart TV 55"))
    }

    @Test
    fun testManufacturerNameParsing() {
        val manager = HelmetAccessoryManager()
        manager.onDeviceConnected("Helmet Device", "00:11:22:33:44:55")

        val cardoManufacturer = "Cardo Systems, Inc.".encodeToByteArray()
        val handled = manager.handleGattNotification(HelmetGattUuids.MANUFACTURER_NAME_CHAR_UUID, cardoManufacturer)
        assertTrue(handled)
        assertEquals(HelmetBrand.CARDO, manager.accessoryState.value.brand)

        val senaManufacturer = "Sena Technologies".encodeToByteArray()
        manager.handleGattNotification(HelmetGattUuids.MANUFACTURER_NAME_CHAR_UUID, senaManufacturer)
        assertEquals(HelmetBrand.SENA, manager.accessoryState.value.brand)

        val ejeasManufacturer = "EJEAS Shenzhen".encodeToByteArray()
        manager.handleGattNotification(HelmetGattUuids.MANUFACTURER_NAME_CHAR_UUID, ejeasManufacturer)
        assertEquals(HelmetBrand.EJEAS, manager.accessoryState.value.brand)
    }

    @Test
    fun testBatteryLevelNotification() {
        val manager = HelmetAccessoryManager()
        manager.onDeviceConnected("Cardo PackTalk", "AA:BB:CC:DD:EE:FF")

        val batteryData = byteArrayOf(85.toByte())
        val handled = manager.handleGattNotification(HelmetGattUuids.BATTERY_LEVEL_CHAR_UUID, batteryData)
        assertTrue(handled)
        assertEquals(85, manager.accessoryState.value.batteryPercent)

        // Clamped max
        manager.handleGattNotification(HelmetGattUuids.BATTERY_LEVEL_CHAR_UUID, byteArrayOf(120.toByte()))
        assertEquals(100, manager.accessoryState.value.batteryPercent)
    }

    @Test
    fun testDeviceInfoNotifications() {
        val manager = HelmetAccessoryManager()
        manager.onDeviceConnected("Sena 50S", "AA:BB:CC:DD:EE:01")

        manager.handleGattNotification(HelmetGattUuids.FIRMWARE_REVISION_CHAR_UUID, "v2.3.1".encodeToByteArray())
        assertEquals("v2.3.1", manager.accessoryState.value.firmwareRevision)

        manager.handleGattNotification(HelmetGattUuids.MODEL_NUMBER_CHAR_UUID, "SP-50S-PRO".encodeToByteArray())
        assertEquals("SP-50S-PRO", manager.accessoryState.value.modelNumber)
    }

    @Test
    fun testCardoButtonEvents() {
        val manager = HelmetAccessoryManager()
        manager.onDeviceConnected("Cardo PackTalk Edge", "AA:BB:CC:DD:EE:02")

        // Press (0x01)
        manager.handleGattNotification(HelmetGattUuids.CARDO_BUTTON_CHAR_UUID, byteArrayOf(0x01))
        assertTrue(manager.accessoryState.value.isPttPressed)

        // Release (0x00)
        manager.handleGattNotification(HelmetGattUuids.CARDO_BUTTON_CHAR_UUID, byteArrayOf(0x00))
        assertFalse(manager.accessoryState.value.isPttPressed)

        // Intercom Click (0x02)
        manager.handleGattNotification(HelmetGattUuids.CARDO_BUTTON_CHAR_UUID, byteArrayOf(0x02))
        assertFalse(manager.accessoryState.value.isPttPressed)
    }

    @Test
    fun testSenaButtonEvents() {
        val manager = HelmetAccessoryManager()
        manager.onDeviceConnected("Sena 50S", "AA:BB:CC:DD:EE:03")

        // Center button intercom PTT down (0x10)
        manager.handleGattNotification(HelmetGattUuids.SENA_BUTTON_CHAR_UUID, byteArrayOf(0x10))
        assertTrue(manager.accessoryState.value.isPttPressed)

        // Button release (0x00)
        manager.handleGattNotification(HelmetGattUuids.SENA_BUTTON_CHAR_UUID, byteArrayOf(0x00))
        assertFalse(manager.accessoryState.value.isPttPressed)
    }

    @Test
    fun testEjeasHandlebarRemoteButtonEvents() {
        val manager = HelmetAccessoryManager()
        manager.onDeviceConnected("EJEAS EUC", "AA:BB:CC:DD:EE:04")

        // Intercom down (0x50)
        manager.handleGattNotification(HelmetGattUuids.EJEAS_BUTTON_CHAR_UUID, byteArrayOf(0x50))
        assertTrue(manager.accessoryState.value.isPttPressed)

        // Intercom up (0x51)
        manager.handleGattNotification(HelmetGattUuids.EJEAS_BUTTON_CHAR_UUID, byteArrayOf(0x51))
        assertFalse(manager.accessoryState.value.isPttPressed)
    }

    @Test
    fun testGenericBleHidReportEvents() {
        val manager = HelmetAccessoryManager()
        manager.onDeviceConnected("BLE Handlebar PTT", "AA:BB:CC:DD:EE:05")

        // Key Down (0x01)
        manager.handleGattNotification(HelmetGattUuids.HID_REPORT_CHAR_UUID, byteArrayOf(0x01))
        assertTrue(manager.accessoryState.value.isPttPressed)

        // Key Up (0x00)
        manager.handleGattNotification(HelmetGattUuids.HID_REPORT_CHAR_UUID, byteArrayOf(0x00))
        assertFalse(manager.accessoryState.value.isPttPressed)
    }

    @Test
    fun testRunawayPttWatchdogSafety() {
        val manager = HelmetAccessoryManager()
        manager.onDeviceConnected("Cardo PackTalk", "AA:BB:CC:DD:EE:06")

        val startTime = 1_000_000L
        manager.handleGattNotification(HelmetGattUuids.CARDO_BUTTON_CHAR_UUID, byteArrayOf(0x01), now = startTime)
        assertTrue(manager.accessoryState.value.isPttPressed)

        // Check at 30s (threshold not reached)
        val triggeredEarly = manager.checkRunawayWatchdog(startTime + 30_000L)
        assertFalse(triggeredEarly)
        assertTrue(manager.accessoryState.value.isPttPressed)

        // Check at 61s (exceeds 60s runaway threshold)
        val triggeredLate = manager.checkRunawayWatchdog(startTime + 65_000L)
        assertTrue(triggeredLate)
        assertFalse(manager.accessoryState.value.isPttPressed, "Watchdog should have forced PTT release")
    }

    @Test
    fun testDeviceDisconnectionAutoRelease() {
        val manager = HelmetAccessoryManager()
        val mac = "AA:BB:CC:DD:EE:07"
        manager.onDeviceConnected("Cardo PackTalk", mac)

        // Press PTT
        manager.handleGattNotification(HelmetGattUuids.CARDO_BUTTON_CHAR_UUID, byteArrayOf(0x01))
        assertTrue(manager.accessoryState.value.isPttPressed)

        // Disconnect
        manager.onDeviceDisconnected(mac)
        assertFalse(manager.accessoryState.value.isConnected)
        assertFalse(manager.accessoryState.value.isPttPressed, "Disconnect should release PTT immediately")
        assertEquals(AudioRoutePreference.BUILTIN_SPEAKER, manager.accessoryState.value.audioRoute)
    }

    @Test
    fun testAudioRoutePreferenceOverride() {
        val manager = HelmetAccessoryManager()
        manager.onDeviceConnected("Cardo PackTalk", "AA:BB:CC:DD:EE:08")
        assertEquals(AudioRoutePreference.BLUETOOTH_SCO, manager.accessoryState.value.audioRoute)

        manager.setAudioRoutePreference(AudioRoutePreference.A2DP_HIGH_FIDELITY)
        assertEquals(AudioRoutePreference.A2DP_HIGH_FIDELITY, manager.accessoryState.value.audioRoute)

        manager.setAudioRoutePreference(AudioRoutePreference.BUILTIN_SPEAKER)
        assertEquals(AudioRoutePreference.BUILTIN_SPEAKER, manager.accessoryState.value.audioRoute)
    }
}
