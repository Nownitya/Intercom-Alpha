package org.nowni.intercom_alpha.power

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.concurrent.AtomicLong

/**
 * Manages iOS UIApplication background task assertions (Keep-Alive)
 * to protect packet forwarding bursts, deduplication cache updates,
 * and audio buffer drains during screen lock and background states.
 */
@OptIn(ExperimentalForeignApi::class)
class BackgroundKeepAlive {

    private var currentTaskId: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private val activeTaskCount = AtomicLong(0)

    private val _isActiveFlow = MutableStateFlow(false)
    val isActiveFlow: StateFlow<Boolean> = _isActiveFlow.asStateFlow()

    val isActive: Boolean
        get() = currentTaskId != UIBackgroundTaskInvalid

    /**
     * Begins a named background task assertion.
     * Guaranteed to cleanly end on expirationHandler to prevent iOS watchdog termination.
     */
    fun beginAssertion(taskName: String = "IntercomAlpha.MeshKeepAlive"): Boolean {
        if (currentTaskId != UIBackgroundTaskInvalid) {
            activeTaskCount.incrementAndGet()
            return true
        }

        val app = UIApplication.sharedApplication
        val taskId = app.beginBackgroundTaskWithName(taskName) {
            // iOS Watchdog Expiration Handler: Must terminate cleanly before iOS kills the process
            endAssertionInternal()
        }

        if (taskId == UIBackgroundTaskInvalid) {
            return false
        }

        currentTaskId = taskId
        activeTaskCount.incrementAndGet()
        _isActiveFlow.value = true
        return true
    }

    /**
     * Ends the current background task assertion.
     */
    fun endAssertion() {
        val remaining = activeTaskCount.decrementAndGet()
        if (remaining <= 0) {
            activeTaskCount.value = 0
            endAssertionInternal()
        }
    }

    /**
     * Executes a suspending action guarded by a background task assertion.
     */
    suspend fun <T> withKeepAlive(
        taskName: String = "IntercomAlpha.BurstForwarding",
        block: suspend () -> T
    ): T {
        beginAssertion(taskName)
        return try {
            block()
        } finally {
            endAssertion()
        }
    }

    private fun endAssertionInternal() {
        if (currentTaskId != UIBackgroundTaskInvalid) {
            val idToEnd = currentTaskId
            currentTaskId = UIBackgroundTaskInvalid
            UIApplication.sharedApplication.endBackgroundTask(idToEnd)
            _isActiveFlow.value = false
        }
    }
}
