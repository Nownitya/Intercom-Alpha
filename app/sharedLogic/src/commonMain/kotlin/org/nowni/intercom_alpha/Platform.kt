package org.nowni.intercom_alpha

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
expect fun currentTimeMillis(): Long
expect fun randomUUID(): String