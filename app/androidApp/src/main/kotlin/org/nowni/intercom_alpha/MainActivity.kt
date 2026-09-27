package org.nowni.intercom_alpha

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import org.nowni.intercom_alpha.service.IntercomForegroundService

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.forEach { (permission, isGranted) ->
            Log.d(TAG, "Permission $permission granted: $isGranted")
            if (!isGranted) allGranted = false
        }
        if (allGranted) {
            Log.d(TAG, "All required permissions granted, checking battery optimization")
            checkBatteryOptimization()
            IntercomForegroundService.startService(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            IntercomAlphaApp()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        permissionsToRequest.add(Manifest.permission.MODIFY_AUDIO_SETTINGS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d(TAG, "Permissions already granted, checking battery optimization")
            checkBatteryOptimization()
            IntercomForegroundService.startService(this)
        }
    }

    private fun checkBatteryOptimization() {
        if (!org.nowni.intercom_alpha.power.PowerManagerHelper.isIgnoringBatteryOptimizations(this)) {
            try {
                val intent = org.nowni.intercom_alpha.power.PowerManagerHelper.createRequestIgnoreBatteryOptimizationsIntent(this)
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to request ignore battery optimizations", e)
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}