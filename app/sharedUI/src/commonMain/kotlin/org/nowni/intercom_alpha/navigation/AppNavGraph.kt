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
import org.nowni.intercom_alpha.feature.home.MainCockpitScreen
import org.nowni.intercom_alpha.feature.home.HomeViewModel
import org.nowni.intercom_alpha.ui.repeater.BasecampRepeaterScreen
import org.nowni.intercom_alpha.ui.repeater.RepeaterViewModel

@Serializable
data object HomeRoute : NavKey

@Serializable
data object RepeaterRoute : NavKey

val appNavSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(HomeRoute::class, HomeRoute.serializer())
        subclass(RepeaterRoute::class, RepeaterRoute.serializer())
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

            MainCockpitScreen(
                uiState = uiState,
                onAction = viewModel::onAction,
                onNavigateToRepeater = { backStack.add(RepeaterRoute) }
            )
        }

        entry<RepeaterRoute> {
            val viewModel: RepeaterViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            BasecampRepeaterScreen(
                uiState = uiState,
                onAction = viewModel::onAction,
                onNavigateBack = {
                    if (backStack.size > 1) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                }
            )
        }
    }

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider
    )
}
