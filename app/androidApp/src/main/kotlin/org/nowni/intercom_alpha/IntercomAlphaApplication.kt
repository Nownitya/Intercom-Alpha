package org.nowni.intercom_alpha

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.nowni.intercom_alpha.di.sharedUiModule

class IntercomAlphaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@IntercomAlphaApplication)
            modules(sharedUiModule)
        }
    }
}
