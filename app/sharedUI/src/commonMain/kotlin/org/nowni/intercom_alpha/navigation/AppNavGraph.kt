package org.nowni.intercom_alpha.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel
import org.nowni.intercom_alpha.feature.home.HomeScreen
import org.nowni.intercom_alpha.feature.home.HomeViewModel

@Serializable
data object HomeRoute : NavKey

val appNavSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(HomeRoute::class, HomeRoute.serializer())
    }
}

val appNavSavedStateConfig = SavedStateConfiguration {
    serializersModule = appNavSerializersModule
}

@Composable
fun AppNavGraph() {
    val backStack = rememberNavBackStack(appNavSavedStateConfig, HomeRoute)

    val entryProvider = entryProvider<NavKey> {
        entry<HomeRoute> {
            val viewModel: HomeViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            HomeScreen(
                uiState = uiState,
                onAction = viewModel::onAction
            )
        }
    }

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider
    )
}
