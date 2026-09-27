package org.nowni.intercom_alpha

import kotlin.js.Date
import kotlin.random.Random

class JsPlatform : Platform {
    override val name: String = "Web JS"
}

actual fun getPlatform(): Platform = JsPlatform()
actual fun currentTimeMillis(): Long = Date.now().toLong()
actual fun randomUUID(): String = "web-uuid-${Random.nextLong()}"