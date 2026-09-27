package org.nowni.intercom_alpha.power

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Manages PARTIAL_WAKE_LOCK lifecycle and Battery Optimization exemptions for Intercom-Alpha.
 * Ensures uninterrupted BLE mesh radio and PCM audio packet processing while screen is off.
 */
class PowerManagerHelper(private val context: Context) {

    private val TAG = "PowerManagerHelper"
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private var wakeLock: PowerManager.WakeLock? = null

    init {
        try {
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            )?.apply {
                setReferenceCounted(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PowerManager.WakeLock", e)
        }
    }

    companion object {
        const val WAKELOCK_TAG = "IntercomAlpha:MeshAudioWakeLock"

        /**
         * Checks whether the application has been granted exemption from battery optimizations.
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
            return true
        }

        /**
         * Creates an intent to request the user to exempt Intercom-Alpha from battery optimizations.
         */
        @SuppressLint("BatteryLife")
        fun createRequestIgnoreBatteryOptimizationsIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        /**
         * Creates an intent to open system battery saver settings page.
         */
        fun createBatteryOptimizationSettingsIntent(): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }

    /**
     * Acquires the PARTIAL_WAKE_LOCK with optional timeout.
     */
    fun acquireWakeLock(timeoutMs: Long = 0) {
        synchronized(this) {
            try {
                wakeLock?.let { lock ->
                    if (!lock.isHeld) {
                        if (timeoutMs > 0) {
                            lock.acquire(timeoutMs)
                            Log.d(TAG, "Acquired PARTIAL_WAKE_LOCK with timeout: ${timeoutMs}ms")
                        } else {
                            lock.acquire()
                            Log.d(TAG, "Acquired PARTIAL_WAKE_LOCK (indefinite)")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception acquiring WakeLock", e)
            }
        }
    }

    /**
     * Releases the PARTIAL_WAKE_LOCK in a safe, leak-prevention manner.
     */
    fun releaseWakeLock() {
        synchronized(this) {
            try {
                wakeLock?.let { lock ->
                    if (lock.isHeld) {
                        lock.release()
                        Log.d(TAG, "Released PARTIAL_WAKE_LOCK")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception releasing WakeLock", e)
            }
        }
    }

    /**
     * Checks if the wake lock is currently held.
     */
    fun isWakeLockHeld(): Boolean {
        return wakeLock?.isHeld == true
    }
}
