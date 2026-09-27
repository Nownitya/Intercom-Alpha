package org.nowni.intercom_alpha

import java.util.UUID

class JvmPlatform : Platform {
    override val name: String = "JVM ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JvmPlatform()
actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual fun randomUUID(): String = UUID.randomUUID().toString()