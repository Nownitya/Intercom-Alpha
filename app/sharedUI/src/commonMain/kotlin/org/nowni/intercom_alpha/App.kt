package org.nowni.intercom_alpha

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import org.koin.compose.KoinContext
import org.nowni.intercom_alpha.navigation.AppNavGraph

@Composable
fun IntercomAlphaApp() {
    KoinContext {
        MaterialTheme {
            Surface {
                AppNavGraph()
            }
        }
    }
}

/**
 * Backward-compatible entrypoint.
 */
@Composable
fun App() {
    IntercomAlphaApp()
}