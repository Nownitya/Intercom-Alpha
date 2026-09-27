package org.nowni.intercom_alpha

import kotlin.random.Random

class WasmPlatform : Platform {
    override val name: String = "Web Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()
actual fun currentTimeMillis(): Long = 0L
actual fun randomUUID(): String = "wasm-uuid-${Random.nextLong()}"