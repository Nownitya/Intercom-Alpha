package org.nowni.intercom_alpha

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Intercom-Alpha",
    ) {
        App()
    }
}