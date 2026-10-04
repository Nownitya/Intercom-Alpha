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
class PowerManagerHelper(
    private val context: Context,
    private val powerManager: PowerManager? = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
) {

    private val TAG = "PowerManagerHelper"

    private var wakeLock: PowerManager.WakeLock? = null
    private var pttWakeLock: PowerManager.WakeLock? = null

    init {
        try {
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            )?.apply {
                setReferenceCounted(true)
            }
            pttWakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                PTT_WAKELOCK_TAG
            )?.apply {
                setReferenceCounted(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PowerManager.WakeLock", e)
        }
    }

    companion object {
        const val WAKELOCK_TAG = "IntercomAlpha:MeshAudioWakeLock"
        const val PTT_WAKELOCK_TAG = "IntercomAlpha:PttTxWakeLock"
        const val DEFAULT_PTT_TIMEOUT_MS = 60_000L

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

    /**
     * Acquires the PTT transmission wake lock with an automatic runaway safety timeout.
     */
    fun acquirePttWakeLock(timeoutMs: Long = DEFAULT_PTT_TIMEOUT_MS) {
        synchronized(this) {
            try {
                pttWakeLock?.let { lock ->
                    if (!lock.isHeld) {
                        lock.acquire(timeoutMs)
                        Log.d(TAG, "Acquired PTT_WAKE_LOCK with timeout: ${timeoutMs}ms")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception acquiring PTT WakeLock", e)
            }
        }
    }

    /**
     * Releases the PTT transmission wake lock, returning to standby state.
     */
    fun releasePttWakeLock() {
        synchronized(this) {
            try {
                pttWakeLock?.let { lock ->
                    if (lock.isHeld) {
                        lock.release()
                        Log.d(TAG, "Released PTT_WAKE_LOCK")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception releasing PTT WakeLock", e)
            }
        }
    }

    /**
     * Checks if the PTT wake lock is currently held.
     */
    fun isPttWakeLockHeld(): Boolean {
        return pttWakeLock?.isHeld == true
    }

    /**
     * Releases both session and PTT wake locks ensuring leak-free teardown.
     */
    fun releaseAllWakeLocks() {
        releasePttWakeLock()
        releaseWakeLock()
    }
}
