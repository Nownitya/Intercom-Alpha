package org.nowni.intercom_alpha.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.nowni.intercom_alpha.feature.home.HomeViewModel

val sharedUiModule = module {
    viewModelOf(::HomeViewModel)
}
