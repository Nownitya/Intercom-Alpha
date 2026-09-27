package org.nowni.intercom_alpha

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform