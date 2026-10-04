package org.nowni.intercom_alpha.bridge

/**
 * Common functional interface for memory-safe reactive subscription cancellation
 * across Kotlin Multiplatform and native Swift/SwiftUI bridge consumers.
 */
fun interface CancellationHandle {
    fun cancel()
}
