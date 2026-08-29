package com.nebulaiq.assignment

import android.app.Application
import com.nebulaiq.assignment.data.messaging.CircleGuardNotificationChannels
import com.nebulaiq.assignment.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class CircleGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CircleGuardNotificationChannels.ensureCreated(this)
        startKoin {
            androidLogger()
            androidContext(this@CircleGuardApplication)
            modules(appModule)
        }
    }
}
